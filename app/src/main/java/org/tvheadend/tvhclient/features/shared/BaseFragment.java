package org.tvheadend.tvhclient.features.shared;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.tvheadend.tvhclient.MainApplication;
import org.tvheadend.tvhclient.R;
import org.tvheadend.tvhclient.data.entity.ServerStatus;
import org.tvheadend.tvhclient.data.repository.AppRepository;
import org.tvheadend.tvhclient.features.shared.callbacks.NetworkAvailabilityChangedInterface;
import org.tvheadend.tvhclient.features.shared.callbacks.NetworkStatusInterface;
import org.tvheadend.tvhclient.features.shared.callbacks.ToolbarInterface;
import org.tvheadend.tvhclient.utils.MenuUtils;

import javax.inject.Inject;

public abstract class BaseFragment extends Fragment implements NetworkAvailabilityChangedInterface {

    protected AppCompatActivity activity;
    protected ToolbarInterface toolbarInterface;
    protected boolean isDualPane;
    protected MenuUtils menuUtils;
    protected boolean isUnlocked;
    protected int htspVersion;
    protected boolean isNetworkAvailable;

    protected ServerStatus serverStatus;

    @Inject
    protected SharedPreferences sharedPreferences;
    @Inject
    protected AppRepository appRepository;

    private FrameLayout mainFrameLayout;
    private FrameLayout detailsFrameLayout;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        activity = (AppCompatActivity) getActivity();
        if (activity instanceof ToolbarInterface) {
            toolbarInterface = (ToolbarInterface) activity;
        }

        MainApplication.getComponent().inject(this);
        if (activity instanceof NetworkStatusInterface) {
            isNetworkAvailable = ((NetworkStatusInterface) activity).isNetworkAvailable();
        }

        mainFrameLayout = activity.findViewById(R.id.main);
        detailsFrameLayout = activity.findViewById(R.id.details);

        serverStatus = appRepository.getServerStatusData().getActiveItem();
        htspVersion = serverStatus.getHtspVersion();
        isUnlocked = MainApplication.getInstance().isUnlocked();
        menuUtils = new MenuUtils(activity);

        // Check if we have a frame in which to embed the details fragment.
        // Make the frame layout visible and set the weights again in case
        // it was hidden by the call to forceSingleScreenLayout()
        isDualPane = detailsFrameLayout != null;
        if (isDualPane) {
            detailsFrameLayout.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0.65f
            );
            mainFrameLayout.setLayoutParams(param);
        }

        setHasOptionsMenu(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                activity.finish();
                return true;

            case R.id.menu_refresh:
                return menuUtils.handleMenuReconnectSelection();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onNetworkAvailabilityChanged(boolean isNetworkAvailable) {
        this.isNetworkAvailable = isNetworkAvailable;
    }

    protected void forceSingleScreenLayout() {
        if (detailsFrameLayout != null) {
            detailsFrameLayout.setVisibility(View.GONE);
            LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1.0f
            );
            mainFrameLayout.setLayoutParams(param);
        }
    }
}
