/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.ServiceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.KeyButtonView;

import java.util.List;

public final class NavigationBarTransitions extends BarTransitions {

    private static final int CONTENT_FADE_DURATION = 200;

    private final NavigationBarView mView;
    private final IStatusBarService mBarService;

    private boolean mLightsOut;
    private boolean mVertical;
    private int mRequestedMode;

    public NavigationBarTransitions(NavigationBarView view) {
        super(view, R.drawable.nav_background);
        mView = view;
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
    }

    public void init(boolean isVertical) {
        setVertical(isVertical);
        applyModeBackground(-1, getMode(), false /*animate*/);
        applyMode(getMode(), false /*animate*/, true /*force*/);
    }

    public void setVertical(boolean isVertical) {
        mVertical = isVertical;
        transitionTo(mRequestedMode, false /*animate*/);
    }

    @Override
    public void transitionTo(int mode, boolean animate) {
        mRequestedMode = mode;
        if (mVertical) {
            // translucent mode not allowed when vertical
            if (mode == MODE_TRANSLUCENT || mode == MODE_TRANSPARENT) {
                mode = MODE_OPAQUE;
            } else if (mode == MODE_LIGHTS_OUT_TRANSPARENT) {
                mode = MODE_LIGHTS_OUT;
            }
        }
        super.transitionTo(mode, animate);
    }

    @Override
    protected void onTransition(int oldMode, int newMode, boolean animate) {
        super.onTransition(oldMode, newMode, animate);
        applyMode(newMode, animate, false /*force*/);
    }

    private void applyMode(int mode, boolean animate, boolean force) {
        // apply to key buttons
        final float alpha = alphaForMode(mode);

        applyBackButtonQuiescentAlpha(mode, animate);
        final View back = mView.getBackButton();
        final View home = mView.getHomeButton();
        final View recent = mView.getRecentsButton();
        final View imeSwitch = mView.getImeSwitchButton();
        if (back != null) {
            setKeyButtonViewQuiescentAlpha(back, alpha, animate);
        }
        if (home != null) {
            setKeyButtonViewQuiescentAlpha(home, alpha, animate);
        }
        if (recent != null) {
            setKeyButtonViewQuiescentAlpha(recent, alpha, animate);
        }
        if (imeSwitch != null) {
            setKeyButtonViewQuiescentAlpha(imeSwitch, alpha, animate);
        }
        List<Integer> buttonIdList = mView.getButtonIdList();
        for (int i = 0; i < buttonIdList.size(); i++) {
            final View customButton = mView.getCustomButton(buttonIdList.get(i));
            if (customButton != null) {
                setKeyButtonViewQuiescentAlpha(customButton, alpha, animate);
            }
        }
        setKeyButtonViewQuiescentAlpha(mView.getLeftMenuButton(), alpha, animate);
        setKeyButtonViewQuiescentAlpha(mView.getRightMenuButton(), alpha, animate);
        setKeyButtonViewQuiescentAlpha(mView.getLeftImeArrowButton(), alpha, animate);
        setKeyButtonViewQuiescentAlpha(mView.getRightImeArrowButton(), alpha, animate);

        // apply to lights out
        applyLightsOut(isLightsOut(mode), animate, force);
    }

    private float alphaForMode(int mode) {
        final boolean isOpaque = mode == MODE_OPAQUE || mode == MODE_LIGHTS_OUT;
        return isOpaque ? KeyButtonView.DEFAULT_QUIESCENT_ALPHA : 1f;
    }

    public void applyBackButtonQuiescentAlpha(int mode, boolean animate) {
        float backAlpha = 0;
        View back = mView.getBackButton();
        View home = mView.getHomeButton();
        View recent = mView.getRecentsButton();
        View ime = mView.getImeSwitchButton();
        View leftMenu = mView.getLeftMenuButton();
        View rightMenu = mView.getRightMenuButton();
        if (home != null) {
            backAlpha = maxVisibleQuiescentAlpha(backAlpha, home);
        }
        if (recent != null) {
            backAlpha = maxVisibleQuiescentAlpha(backAlpha, recent);
        }
        if (rightMenu != null) {
            backAlpha = maxVisibleQuiescentAlpha(backAlpha, mView.getRightMenuButton());
        }
        if (leftMenu != null) {
            backAlpha = maxVisibleQuiescentAlpha(backAlpha, mView.getLeftMenuButton());
        }
        if (ime != null) {
            backAlpha = maxVisibleQuiescentAlpha(backAlpha, mView.getImeSwitchButton());
        }
        if (backAlpha > 0) {
            if (back != null) {
                setKeyButtonViewQuiescentAlpha(back, backAlpha, animate);
            }
        }
    }

    private static float maxVisibleQuiescentAlpha(float max, View v) {
        if ((v instanceof KeyButtonView) && v.isShown()) {
            return Math.max(max, ((KeyButtonView)v).getQuiescentAlpha());
        }
        return max;
    }

    private void setKeyButtonViewQuiescentAlpha(View button, float alpha, boolean animate) {
        if (button instanceof KeyButtonView) {
            ((KeyButtonView) button).setQuiescentAlpha(alpha, animate);
        }
    }

    private void applyLightsOut(boolean lightsOut, boolean animate, boolean force) {
        if (!force && lightsOut == mLightsOut) return;

        mLightsOut = lightsOut;

        final View navButtons = mView.getCurrentView().findViewById(R.id.nav_buttons);
        final View lowLights = mView.getCurrentView().findViewById(R.id.lights_out);

        // ok, everyone, stop it right there
        navButtons.animate().cancel();
        lowLights.animate().cancel();

        final float navButtonsAlpha = lightsOut ? 0f : 1f;
        final float lowLightsAlpha = lightsOut ? 1f : 0f;

        if (!animate) {
            navButtons.setAlpha(navButtonsAlpha);
            lowLights.setAlpha(lowLightsAlpha);
            lowLights.setVisibility(lightsOut ? View.VISIBLE : View.GONE);
        } else {
            final int duration = lightsOut ? LIGHTS_OUT_DURATION : LIGHTS_IN_DURATION;
            navButtons.animate()
                .alpha(navButtonsAlpha)
                .setDuration(duration)
                .start();

            lowLights.setOnTouchListener(mLightsOutListener);
            if (lowLights.getVisibility() == View.GONE) {
                lowLights.setAlpha(0f);
                lowLights.setVisibility(View.VISIBLE);
            }
            lowLights.animate()
                .alpha(lowLightsAlpha)
                .setDuration(duration)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .setListener(lightsOut ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        lowLights.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    }

    private final View.OnTouchListener mLightsOutListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                // even though setting the systemUI visibility below will turn these views
                // on, we need them to come up faster so that they can catch this motion
                // event
                applyLightsOut(false, false, false);

                try {
                    mBarService.setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE,
                            "LightsOutListener");
                } catch (android.os.RemoteException ex) {
                }
            }
            return false;
        }
    };
}
