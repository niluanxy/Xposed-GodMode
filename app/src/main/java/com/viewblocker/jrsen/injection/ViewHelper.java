package com.viewblocker.jrsen.injection;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import com.viewblocker.jrsen.rule.ViewRule;

import de.robv.android.xposed.XposedHelpers;

/**
 * Created by jrsen on 17-10-13.
 */

public final class ViewHelper {

    @SuppressWarnings("unchecked")
    public static <V extends View> V findViewById(Activity activity, int id) {
        return (V) activity.findViewById(id);
    }

    @SuppressWarnings("unchecked")
    public static <V extends View> V findViewByPath(Activity activity, int[] depths) {
        View targetView = activity.getWindow().getDecorView();
        for (int depth : depths) {
            targetView = targetView instanceof ViewGroup
                    ? ((ViewGroup) targetView).getChildAt(depth) : null;
            if (targetView == null) break;
        }
        return (V) targetView;
    }

    public static View findTopParentViewByChildView(View v) {
        if (v.getParent() == null || !(v.getParent() instanceof ViewGroup)) {
            return v;
        } else {
            return findTopParentViewByChildView((View) v.getParent());
        }
    }

    public static Object findViewRootImplByChildView(ViewParent parent) {
        if (parent.getParent() == null) {
            return !(parent instanceof ViewGroup) ? parent : null;
        } else {
            return findViewRootImplByChildView(parent.getParent());
        }
    }

    public static int[] getViewHierarchyDepth(View view) {
        int[] depth = new int[0];
        ViewParent parent = view.getParent();
        while (parent instanceof ViewGroup) {
            int[] newDepth = new int[depth.length + 1];
            System.arraycopy(depth, 0, newDepth, 1, depth.length);
            newDepth[0] = ((ViewGroup) parent).indexOfChild(view);
            depth = newDepth;
            view = (View) parent;
            parent = parent.getParent();
        }
        return depth;
    }

    public static ViewRule makeRule(View v) {
        Activity activity = getAttachedActivityFromView(v);
        if (activity == null) {
            return null;
        }

        String alias;
        if (v instanceof TextView) {
            CharSequence text = ((TextView) v).getText();
            alias = text != null ? text.toString() : "";
        } else {
            CharSequence description = v.getContentDescription();
            alias = description != null ? description.toString() : "";
        }

        int[] out = new int[2];
        v.getLocationInWindow(out);

        int x = out[0];
        int y = out[1];
        int width = v.getWidth();
        int height = v.getHeight();

        int[] viewHierarchyDepth = getViewHierarchyDepth(v);
        String activityClassName = activity.getComponentName().getClassName();
        String viewClassName = v.getClass().getName();
        Resources res = v.getContext().getResources();
        String resourceName = null;
        try {
            resourceName = v.getId() != View.NO_ID ? res.getResourceName(v.getId()) : null;
        } catch (Resources.NotFoundException ignore) {
            //可能资源id来自plugin所以找不到
        }
        return new ViewRule("", alias, x, y, width, height, activityClassName, viewClassName
                , viewHierarchyDepth, resourceName, View.INVISIBLE, System.currentTimeMillis());
    }

    public static Bitmap markViewBounds(Bitmap bitmap, int left, int top, int right, int bottom) {
        Paint markPaint = new Paint();
        markPaint.setColor(Color.RED);
        markPaint.setAlpha(100);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawRect(left, top, right, bottom, markPaint);
        return bitmap;
    }

    public static Activity getAttachedActivityFromView(View view) {
        Activity activity = getActivityFromViewContext(view.getContext());
        if (activity != null) {
            return activity;
        } else {
            ViewParent parent = view.getParent();
            return parent instanceof ViewGroup ? getAttachedActivityFromView((View) parent) : null;
        }
    }

    private static Activity getActivityFromViewContext(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        } else if (context instanceof ContextWrapper) {
            //这是不直接getBaseContext方法获取 因为撒比微信有个PluginContextWrapper getBaseContext返回的是this导致栈溢出
            Context baseContext = ((ContextWrapper) context).getBaseContext();
            if (baseContext == context) {
                baseContext = (Context) XposedHelpers.getObjectField(context, "mBase");
            }
            return getActivityFromViewContext(baseContext);
        } else {
            return null;
        }
    }

    public static Bitmap snapshotView(View view) {
        boolean enable = view.isDrawingCacheEnabled();
        view.setDrawingCacheEnabled(true);
        Bitmap b = view.getDrawingCache();
        b = b == null ? snapshotViewCompat(view) : Bitmap.createBitmap(b);
        view.setDrawingCacheEnabled(enable);
        return b;
    }

    private static Bitmap snapshotViewCompat(View v) {
        //有些view宽高为0神奇!!!
        Bitmap b = Bitmap.createBitmap(Math.max(v.getWidth(), 1), Math.max(v.getHeight(), 1), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.draw(c);
        return b;
    }

    public static Bitmap cloneViewAsBitmap(View view) {
        Bitmap bitmap = snapshotView(view);

        Paint paint = new Paint();
        paint.setAntiAlias(false);

        // Draw optical bounds
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);

        Canvas canvas = new Canvas(bitmap);
        drawRect(canvas, paint, 0, 0, canvas.getWidth() - 1, canvas.getHeight() - 1);

//        // Draw margins
//        {
//            paint.setColor(Color.argb(63, 255, 0, 255));
//            paint.setStyle(Paint.Style.FILL);
//
//            onDebugDrawMargins(canvas, paint);
//        }

        // Draw clip bounds
        paint.setColor(Color.rgb(63, 127, 255));
        paint.setStyle(Paint.Style.FILL);

        Context context = view.getContext();
        int lineLength = dipsToPixels(context, 8);
        int lineWidth = dipsToPixels(context, 1);
        drawRectCorners(canvas, 0, 0, canvas.getWidth(), canvas.getHeight(),
                paint, lineLength, lineWidth);
        return bitmap;
    }

    private static int dipsToPixels(Context context, int dips) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dips * scale + 0.5f);
    }

    private static void drawRect(Canvas canvas, Paint paint, int x1, int y1, int x2, int y2) {
        float[] debugLines = new float[16];

        debugLines[0] = x1;
        debugLines[1] = y1;
        debugLines[2] = x2;
        debugLines[3] = y1;

        debugLines[4] = x2;
        debugLines[5] = y1;
        debugLines[6] = x2;
        debugLines[7] = y2;

        debugLines[8] = x2;
        debugLines[9] = y2;
        debugLines[10] = x1;
        debugLines[11] = y2;

        debugLines[12] = x1;
        debugLines[13] = y2;
        debugLines[14] = x1;
        debugLines[15] = y1;

        canvas.drawLines(debugLines, paint);
    }

    private static void drawRectCorners(Canvas canvas, int x1, int y1, int x2, int y2, Paint paint,
                                        int lineLength, int lineWidth) {
        drawCorner(canvas, paint, x1, y1, lineLength, lineLength, lineWidth);
        drawCorner(canvas, paint, x1, y2, lineLength, -lineLength, lineWidth);
        drawCorner(canvas, paint, x2, y1, -lineLength, lineLength, lineWidth);
        drawCorner(canvas, paint, x2, y2, -lineLength, -lineLength, lineWidth);
    }

    private static void drawCorner(Canvas c, Paint paint, int x1, int y1, int dx, int dy, int lw) {
        fillRect(c, paint, x1, y1, x1 + dx, y1 + lw * sign(dy));
        fillRect(c, paint, x1, y1, x1 + lw * sign(dx), y1 + dy);
    }

    private static void fillRect(Canvas canvas, Paint paint, int x1, int y1, int x2, int y2) {
        if (x1 != x2 && y1 != y2) {
            if (x1 > x2) {
                int tmp = x1;
                x1 = x2;
                x2 = tmp;
            }
            if (y1 > y2) {
                int tmp = y1;
                y1 = y2;
                y2 = tmp;
            }
            canvas.drawRect(x1, y1, x2, y2, paint);
        }
    }

    private static int sign(int x) {
        return (x >= 0) ? 1 : -1;
    }

}
