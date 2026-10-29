package org.schabi.newpipe.fragments.detail;

import android.os.Parcelable;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.fragment.app.FragmentTransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A pager adapter that lets offscreen fragments save state instead of keeping every tab resident.
 */
public final class StateSavingTabAdapter extends FragmentStatePagerAdapter {
    private final FragmentManager fragmentManager;
    private final List<Fragment> fragmentList = new ArrayList<>();
    private final List<String> fragmentTitleList = new ArrayList<>();
    private final Map<Fragment, Fragment.SavedState> savedStates = new IdentityHashMap<>();
    private final Set<Fragment> instantiatedFragments =
            Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable
    private FragmentTransaction curTransaction = null;
    @Nullable
    private Fragment currentPrimaryItem = null;

    public StateSavingTabAdapter(final FragmentManager fm) {
        // Keep legacy visibility semantics because several tabs still depend on them.
        super(fm, BEHAVIOR_SET_USER_VISIBLE_HINT);
        this.fragmentManager = fm;
    }

    @NonNull
    @Override
    public Fragment getItem(final int position) {
        return fragmentList.get(position);
    }

    @Override
    public int getCount() {
        return fragmentList.size();
    }

    @NonNull
    @Override
    @SuppressWarnings("deprecation")
    public Object instantiateItem(@NonNull final ViewGroup container, final int position) {
        final Fragment fragment = getItem(position);
        if (instantiatedFragments.contains(fragment)) {
            return fragment;
        }

        if (curTransaction == null) {
            curTransaction = fragmentManager.beginTransaction();
        }

        final Fragment.SavedState fss = savedStates.get(fragment);
        if (fss != null) {
            fragment.setInitialSavedState(fss);
        }

        fragment.setMenuVisibility(false);
        fragment.setUserVisibleHint(false);
        instantiatedFragments.add(fragment);
        curTransaction.add(container.getId(), fragment);

        return fragment;
    }

    @Override
    public void destroyItem(@NonNull final ViewGroup container,
                            final int position,
                            @NonNull final Object object) {
        final Fragment fragment = (Fragment) object;

        if (curTransaction == null) {
            curTransaction = fragmentManager.beginTransaction();
        }

        if (fragmentList.contains(fragment)) {
            savedStates.put(fragment, fragment.isAdded()
                    ? fragmentManager.saveFragmentInstanceState(fragment) : null);
        } else {
            savedStates.remove(fragment);
        }
        instantiatedFragments.remove(fragment);

        curTransaction.remove(fragment);
        if (fragment.equals(currentPrimaryItem)) {
            currentPrimaryItem = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setPrimaryItem(@NonNull final ViewGroup container,
                               final int position,
                               @NonNull final Object object) {
        final Fragment fragment = (Fragment) object;
        if (fragment != currentPrimaryItem) {
            if (currentPrimaryItem != null) {
                currentPrimaryItem.setMenuVisibility(false);
                currentPrimaryItem.setUserVisibleHint(false);
            }
            fragment.setMenuVisibility(true);
            fragment.setUserVisibleHint(true);
            currentPrimaryItem = fragment;
        }
    }

    @Override
    public void finishUpdate(@NonNull final ViewGroup container) {
        if (curTransaction != null) {
            curTransaction.commitNowAllowingStateLoss();
            curTransaction = null;
        }
    }

    @Nullable
    @Override
    public Parcelable saveState() {
        return null;
    }

    @Override
    public void restoreState(@Nullable final Parcelable state,
                             @Nullable final ClassLoader loader) {
    }

    public void addFragment(final Fragment fragment, final String title) {
        fragmentList.add(fragment);
        fragmentTitleList.add(title);
    }

    public void clearAllItems() {
        fragmentList.clear();
        fragmentTitleList.clear();
        savedStates.clear();
    }

    public void updateItem(final int position, final Fragment fragment) {
        final Fragment oldFragment = fragmentList.set(position, fragment);
        savedStates.remove(oldFragment);
    }

    public void updateItem(final String title, final Fragment fragment) {
        final int index = fragmentTitleList.indexOf(title);
        if (index != -1) {
            updateItem(index, fragment);
        }
    }

    @Nullable
    public Fragment getFragment(final String title) {
        final int index = fragmentTitleList.indexOf(title);
        if (index == -1) {
            return null;
        }
        return fragmentList.get(index);
    }

    @Override
    public int getItemPosition(@NonNull final Object object) {
        if (fragmentList.contains(object)) {
            return fragmentList.indexOf(object);
        } else {
            return POSITION_NONE;
        }
    }

    public int getItemPositionByTitle(final String title) {
        return fragmentTitleList.indexOf(title);
    }

    @Nullable
    public String getItemTitle(final int position) {
        if (position < 0 || position >= fragmentTitleList.size()) {
            return null;
        }
        return fragmentTitleList.get(position);
    }

    public void notifyDataSetUpdate() {
        notifyDataSetChanged();
    }
}
