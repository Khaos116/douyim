package com.zz.douyin.hook;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class TabDetector {
    private static final double TAB_TOP_FRACTION = 0.25;
    private static final int MAX_ANCESTOR_LEVELS = 16;

    private TabDetector() {
    }

    static boolean isLiveTab(View decor) {
        for (Candidate candidate : collectLiveTabViews(decor)) {
            if (candidate.selected) {
                return true;
            }
        }
        return false;
    }

    static boolean isLiveTabLabel(CharSequence text) {
        return text != null && "直播".equals(text.toString().trim());
    }

    /**
     * Pure match rule, JVM-testable. The host may hide its own tab strip
     * while playing, so visibility is not required — but the view must be
     * attached and have nonzero bounds, and selected itself or through an
     * ancestor belonging to the same individual tab item.
     */
    static boolean matchesCandidate(
            int top,
            int decorHeight,
            int width,
            int height,
            boolean attached,
            boolean selectedSelf,
            boolean selectedAncestor) {
        return attached
                && width > 0
                && height > 0
                && top >= 0
                && decorHeight > 0
                && top < decorHeight * TAB_TOP_FRACTION
                && (selectedSelf || selectedAncestor);
    }

    /**
     * One-line diagnosis for filter logs: which "直播" views exist in the
     * tree and why each (mis)matched. Only called on live-filter decisions.
     */
    static String diagnoseLiveTab(View decor) {
        List<Candidate> found = collectLiveTabViews(decor);
        StringBuilder out = new StringBuilder("live-views=").append(found.size());
        for (Candidate candidate : found) {
            out.append(" [top=").append(candidate.top)
                    .append(" wh=").append(candidate.width).append('x').append(candidate.height)
                    .append(" shown=").append(candidate.shown)
                    .append(" sel=").append(candidate.selfSelected)
                    .append(" anc=").append(candidate.ancestorSelected)
                    .append(']');
        }
        return out.toString();
    }

    private static List<Candidate> collectLiveTabViews(View decor) {
        List<Candidate> found = new ArrayList<>();
        if (decor == null || decor.getHeight() <= 0) {
            return found;
        }
        collect(decor, decor.getWidth(), decor.getHeight(), found, new int[2]);
        return found;
    }

    private static void collect(
            View node, int decorWidth, int decorHeight,
            List<Candidate> found, int[] location) {
        if (node instanceof TextView text && isLiveTabLabel(text.getText())) {
            node.getLocationOnScreen(location);
            boolean selfSelected = node.isSelected();
            boolean ancestorSelected = hasSelectedAncestor(node, decorWidth, decorHeight);
            boolean selected = matchesCandidate(
                    location[1],
                    decorHeight,
                    node.getWidth(),
                    node.getHeight(),
                    isAttached(node),
                    selfSelected,
                    ancestorSelected);
            found.add(new Candidate(
                    location[1],
                    node.getWidth(),
                    node.getHeight(),
                    node.isShown(),
                    selfSelected,
                    ancestorSelected,
                    selected));
        }
        if (node instanceof ViewGroup group) {
            for (int index = 0, count = group.getChildCount();
                    index < count;
                    index++) {
                collect(group.getChildAt(index), decorWidth, decorHeight, found, location);
            }
        }
    }

    private static boolean isAttached(View node) {
        return node.isAttachedToWindow() || node.getWindowToken() != null;
    }

    static boolean isTabSelectionOwner(int width, int height, int decorWidth, int decorHeight) {
        return FeedUiHider.isTabItemSize(width, height, decorWidth, decorHeight);
    }

    private static boolean hasSelectedAncestor(View node, int decorWidth, int decorHeight) {
        Object parent = node.getParent();
        for (int level = 0;
                level < MAX_ANCESTOR_LEVELS && parent instanceof View;
                level++) {
            View ancestor = (View) parent;
            // Stop at the shared tab strip/page, even if that container is selected.
            // ponytail: use the existing tab-item size bound; revise with real hierarchy logs if a host changes it.
            if (!isTabSelectionOwner(
                    ancestor.getWidth(), ancestor.getHeight(), decorWidth, decorHeight)) {
                break;
            }
            if (ancestor.isSelected()) {
                return true;
            }
            parent = ancestor.getParent();
        }
        return false;
    }

    private static final class Candidate {
        final int top;
        final int width;
        final int height;
        final boolean shown;
        final boolean selfSelected;
        final boolean ancestorSelected;
        final boolean selected;

        Candidate(
                int top,
                int width,
                int height,
                boolean shown,
                boolean selfSelected,
                boolean ancestorSelected,
                boolean selected) {
            this.top = top;
            this.width = width;
            this.height = height;
            this.shown = shown;
            this.selfSelected = selfSelected;
            this.ancestorSelected = ancestorSelected;
            this.selected = selected;
        }
    }
}
