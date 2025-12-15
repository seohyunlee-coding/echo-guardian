package com.example.imageblog;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Grid 간격을 균등하게 넣어주는 ItemDecoration
 * spanCount와 spacing(px), edge 포함 여부를 설정할 수 있음
 */
public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
    private final int spanCount;
    private final int spacing;
    private final boolean includeEdge;

    public SpacesItemDecoration(int spacing) {
        this(2, spacing, false); // edge 제외로 변경하여 더 깔끔하게
    }

    public SpacesItemDecoration(int spanCount, int spacing, boolean includeEdge) {
        this.spanCount = spanCount;
        this.spacing = spacing;
        this.includeEdge = includeEdge;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view); // item position
        if (position == RecyclerView.NO_POSITION) return;

        int column = position % spanCount; // item column

        if (includeEdge) {
            outRect.left = spacing - column * spacing / spanCount;
            outRect.right = (column + 1) * spacing / spanCount;

            if (position < spanCount) { // top edge
                outRect.top = spacing;
            }
            outRect.bottom = spacing; // item bottom
        } else {
            outRect.left = column * spacing / spanCount;
            outRect.right = spacing - (column + 1) * spacing / spanCount;
            if (position >= spanCount) {
                outRect.top = spacing / 2; // item top - 간격을 절반으로 줄임
            }
            outRect.bottom = spacing / 2; // item bottom - 간격을 절반으로 줄임
        }
    }
}
