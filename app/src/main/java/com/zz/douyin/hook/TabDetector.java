package com.zz.douyin.hook;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

final class TabDetector {
    private TabDetector() {
    }

    static boolean isLiveTab(View decor) {
        if (decor == null || decor.getHeight() <= 0) {
            return false;
        }
        return collectLiveTab(decor, decor.getHeight());
    }

    static boolean isLiveTabLabel(CharSequence text) {
        return text != null && "直播".equals(text.toString().trim());
    }

    private static boolean collectLiveTab(View node, int decorHeight) {
        if (node instanceof TextView && node.isShown()) {
            int[] location = new int[2];
            node.getLocationOnScreen(location);
            if (location[1] < decorHeight * 0.25
                    && isLiveTabLabel(((TextView) node).getText())
                    && (node.isSelected() || hasSelectedAncestor(node, 2))) {
                return true;
            }
        }
        if (node instanceof ViewGroup group) {
            for (int index = 0, count = group.getChildCount();
                    index < count;
                    index++) {
                if (collectLiveTab(group.getChildAt(index), decorHeight)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasSelectedAncestor(View node, int levels) {
        Object parent = node.getParent();
        for (int level = 0; level < levels && parent instanceof View; level++) {
            if (((View) parent).isSelected()) {
                return true;
            }
            parent = ((View) parent).getParent();
        }
        return false;
    }
}
