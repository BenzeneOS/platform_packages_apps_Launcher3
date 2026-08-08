/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps.search;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.getSize;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherPrefs.ALL_APPS_BOTTOM_SEARCH;
import static com.android.launcher3.Utilities.prefixTextWithIcon;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.TextKeyListener;
import android.text.style.ReplacementSpan;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.SearchUiManager;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Layout to contain the All-apps search UI.
 */
public class AppsSearchContainerLayout extends ExtendedEditText
        implements SearchUiManager, SearchCallback<AdapterItem>,
        AllAppsStore.OnUpdateListener, Insettable {

    private boolean mIsSearchSessionActive = false;

    private final ActivityContext mLauncher;
    private final AllAppsSearchBarController mSearchBarController;
    private final SpannableStringBuilder mSearchQueryBuilder;
    private final CharSequence mHintWithStartIcon;
    private final CharSequence mBottomHintWithStartIcon;
    private final CharSequence mFocusedHint;

    private ActivityAllAppsContainerView<?> mAppsView;

    // The amount of pixels to shift down and overlap with the rest of the content.
    private final int mContentOverlap;
    private int mBottomSearchBaseBottomMargin;
    private int mBottomSearchImeInset;
    private boolean mResettingSearch;

    public AppsSearchContainerLayout(Context context) {
        this(context, null);
    }

    public AppsSearchContainerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsSearchContainerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = ActivityContext.lookupContext(context);
        mSearchBarController = new AllAppsSearchBarController();

        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);
        mHintWithStartIcon =
                prefixTextWithIcon(context, R.drawable.ic_allapps_search, getHint());
        mBottomHintWithStartIcon = getCenteredHintWithStartIcon(context);
        mFocusedHint = context.getText(R.string.all_apps_search_bar_hint);
        updateHintForFocusState();

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && !s.isEmpty()) {
                    mIsSearchSessionActive = true;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mContentOverlap =
                getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_content_overlap);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        updateHintForFocusState();
    }

    private void updateHintForFocusState() {
        setHint(isBottomSearchEnabled()
                ? (hasFocus() ? mFocusedHint : mBottomHintWithStartIcon)
                : mHintWithStartIcon);
    }

    @Override
    protected void viewClicked(InputMethodManager imm) {
        super.viewClicked(imm);
        if (!isBottomSearchEnabled() && !mIsSearchSessionActive) {
            mIsSearchSessionActive = true;
            // Non-null list to trigger animateToSearchState.
            mAppsView.setSearchResults(Collections.emptyList());
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAppsView.getAppsStore().addUpdateListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAppsView.getAppsStore().removeUpdateListener(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Update the width to match the grid padding
        DeviceProfile dp = mLauncher.getDeviceProfile();
        int myRequestedWidth = getSize(widthMeasureSpec);
        int rowWidth = myRequestedWidth;
        if (isBottomSearchEnabled()) {
            rowWidth -= dp.getAllAppsProfile().getPadding().left
                    + dp.getAllAppsProfile().getPadding().right;
        } else if (mAppsView != null && mAppsView.getActiveRecyclerView() != null) {
            rowWidth -= mAppsView.getActiveRecyclerView().getPaddingLeft()
                    + mAppsView.getActiveRecyclerView().getPaddingRight();
        }
        rowWidth = Math.max(0, rowWidth);

        int cellWidth = DeviceProfile.calculateCellWidth(rowWidth,
                dp.getWorkspaceProfile().getCellLayoutBorderSpacePx().x,
                dp.getHotseatProfile().getNumShownIcons());
        int iconVisibleSize =
                Math.round(ICON_VISIBLE_AREA_FACTOR * dp.getWorkspaceProfile().getIconSizePx());
        int iconPadding = cellWidth - iconVisibleSize;

        int myWidth = rowWidth - iconPadding + getPaddingLeft() + getPaddingRight();
        super.onMeasure(makeMeasureSpec(myWidth, EXACTLY), heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // Shift the widget horizontally so that its centered in the parent (b/63428078)
        View parent = (View) getParent();
        int availableWidth = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        int myWidth = right - left;
        int expectedLeft = parent.getPaddingLeft() + (availableWidth - myWidth) / 2;
        int shift = expectedLeft - left;
        setTranslationX(shift);

        if (!isBottomSearchEnabled()) {
            offsetTopAndBottom(mContentOverlap);
        }
    }

    private CharSequence getCenteredHintWithStartIcon(Context context) {
        CharSequence hint = context.getText(R.string.all_apps_search_bar_hint);
        SpannableString spanned = new SpannableString(hint);
        spanned.setSpan(new CenteredIconHintSpan(context, R.drawable.ic_allapps_search),
                0, spanned.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spanned;
    }

    private static class CenteredIconHintSpan extends ReplacementSpan {
        private final Drawable mIcon;
        private final int mIconTextGap;
        private int mOldTint;

        CenteredIconHintSpan(Context context, int iconRes) {
            mIcon = context.getDrawable(iconRes).mutate();
            mIconTextGap = context.getResources().getDimensionPixelSize(
                    R.dimen.all_apps_search_icon_text_padding);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, FontMetricsInt fm) {
            FontMetricsInt metrics = paint.getFontMetricsInt();
            if (fm != null) {
                fm.ascent = metrics.ascent;
                fm.descent = metrics.descent;
                fm.top = metrics.top;
                fm.bottom = metrics.bottom;
            }
            int iconSize = metrics.bottom - metrics.top;
            mIcon.setBounds(0, 0, iconSize, iconSize);
            return iconSize + mIconTextGap
                    + (int) Math.ceil(paint.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                float x, int top, int y, int bottom, Paint paint) {
            int color = paint.getColor();
            if (mOldTint != color) {
                mOldTint = color;
                mIcon.setTint(color);
            }

            FontMetricsInt metrics = paint.getFontMetricsInt();
            int iconSize = metrics.bottom - metrics.top;
            mIcon.setBounds(0, 0, iconSize, iconSize);

            canvas.save();
            canvas.translate(x, y + metrics.top);
            mIcon.draw(canvas);
            canvas.restore();

            canvas.drawText(text, start, end, x + iconSize + mIconTextGap, y, paint);
        }
    }

    @Override
    public void initializeSearch(ActivityAllAppsContainerView<?> appsView) {
        mAppsView = appsView;
        int maxResultsCount = 5;
        if (isBottomSearchEnabled()) {
            int horizontalPadding =
                    getResources().getDimensionPixelSize(R.dimen.all_apps_search_bar_padding);
            setIncludeFontPadding(false);
            setPaddingRelative(horizontalPadding, 0, horizontalPadding, 0);
            setWindowInsetsAnimationCallback(null);
            maxResultsCount = Math.max(1, mLauncher.getDeviceProfile().getAllAppsProfile()
                    .getNumShownAllAppsColumns());
        }
        updateHintForFocusState();
        mSearchBarController.initialize(
                new DefaultAppSearchAlgorithm(
                        getContext(), mLauncher.getUiExecutor(), true, maxResultsCount),
                this, mLauncher, this);
    }

    @Override
    public void onAppsUpdated() {
        mSearchBarController.refreshSearchResult();
    }

    @Override
    public void resetSearch() {
        // The doc comment for this method in SearchUiManager says this should close any active
        // search session.
        mIsSearchSessionActive = false;
        if (!isBottomSearchEnabled()) {
            mSearchBarController.reset();
            return;
        }
        mResettingSearch = true;
        try {
            mSearchBarController.reset();
        } finally {
            mResettingSearch = false;
        }
    }

    @Override
    public boolean isSearchQueryEmpty() {
        String query = Utilities.trim(getEditableText().toString());
        return query.isEmpty();
    }

    @Override
    public boolean shouldInterceptBackButton() {
        return mIsSearchSessionActive;
    }

    @Override
    public void preDispatchKeyEvent(KeyEvent event) {
        // Determine if the key event was actual text, if so, focus the search bar and then dispatch
        // the key normally so that it can process this key event
        if (!mSearchBarController.isSearchFieldFocused() &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            final int unicodeChar = event.getUnicodeChar();
            final boolean isKeyNotWhitespace = unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) && !Character.isSpaceChar(unicodeChar);
            if (isKeyNotWhitespace) {
                boolean gotKey = TextKeyListener.getInstance().onKeyDown(this, mSearchQueryBuilder,
                        event.getKeyCode(), event);
                if (gotKey && mSearchQueryBuilder.length() > 0) {
                    mSearchBarController.focusSearchField();
                }
            }
        }
    }

    @Override
    public void onSearchResult(String query, ArrayList<AdapterItem> items) {
        if (items != null) {
            mAppsView.setSearchResults(items);
        }
    }

    @Override
    public void clearSearchResult() {
        // The doc comment for clearSearchResult in the SearchCallback interface says:
        // "Called when the search results should be cleared." This is also called when the search
        // query is empty by AllAppsSearchBarController#afterTextChanged.

        // Clear the search query
        mSearchQueryBuilder.clear();
        mSearchQueryBuilder.clearSpans();
        Selection.setSelection(mSearchQueryBuilder, 0);

        if (isBottomSearchEnabled()) {
            boolean keepEditing = !mResettingSearch && mIsSearchSessionActive;
            mIsSearchSessionActive = false;
            mAppsView.onClearSearchResult(keepEditing);
            if (keepEditing) {
                requestFocus();
            }
        } else if (!mIsSearchSessionActive) {
            mAppsView.onClearSearchResult();
        } else {
            // Keep the active search session while removing stale results for an empty query.
            mAppsView.setSearchResults(null);
        }
    }

    @Override
    public void setInsets(Rect insets) {
        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        if (isBottomSearchEnabled()) {
            mlp.topMargin = 0;
            mBottomSearchBaseBottomMargin = Math.max(0, insets.bottom);
            updateBottomSearchBottomMargin();
            return;
        }

        mlp.bottomMargin = 0;
        mBottomSearchBaseBottomMargin = 0;
        mBottomSearchImeInset = 0;
        // Use bottom_sheet_handle_area_height instead which is what NexusLauncher's
        // UniversalSearchInputView does. This puts it closer to the bottom sheet handle, as it
        // makes it flush against the handle area.
        mlp.topMargin = getResources().getDimensionPixelSize(
                R.dimen.bottom_sheet_handle_area_height);
        requestLayout();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (isBottomSearchEnabled()) {
            setTranslationY(0);
            setBottomSearchImeInset(insets.getInsets(WindowInsets.Type.ime()).bottom);
        }
        return super.onApplyWindowInsets(insets);
    }

    private void setBottomSearchImeInset(int imeInset) {
        int bottomSearchImeInset = Math.max(0, imeInset);
        if (mBottomSearchImeInset != bottomSearchImeInset) {
            mBottomSearchImeInset = bottomSearchImeInset;
            updateBottomSearchBottomMargin();
        }
    }

    private void updateBottomSearchBottomMargin() {
        if (!isBottomSearchEnabled() || !(getLayoutParams() instanceof MarginLayoutParams)) {
            return;
        }

        MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        int bottomMargin = Math.max(mBottomSearchBaseBottomMargin, mBottomSearchImeInset)
                + getResources().getDimensionPixelSize(
                        R.dimen.all_apps_bottom_search_bar_bottom_padding);
        if (mlp.bottomMargin != bottomMargin) {
            mlp.bottomMargin = bottomMargin;
            setLayoutParams(mlp);
        } else {
            requestLayout();
        }
    }

    private boolean isBottomSearchEnabled() {
        return mAppsView != null
                ? mAppsView.isBottomSearchEnabled()
                : ALL_APPS_BOTTOM_SEARCH.get(getContext());
    }

    @Override
    public ExtendedEditText getEditText() {
        return this;
    }
}
