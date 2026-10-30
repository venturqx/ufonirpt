package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.TournesolFilterConfig;
import org.schabi.newpipe.util.TournesolHelper;

public class DefaultKioskFragment extends KioskFragment {

    @Nullable
    private TournesolFilterController tournesolFilterController;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (serviceId < 0) {
            updateSelectedDefaultKiosk();
        }
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        if ("Tournesol".equals(kioskId)) {
            ensureTournesolController(rootView);
            applyTournesolFilters(tournesolFilterController.getCurrentConfig(), false);
        } else {
            hideTournesolHeader(rootView);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (serviceId != ServiceHelper.getSelectedServiceId(requireContext())) {
            if (currentWorker != null) {
                currentWorker.dispose();
            }
            updateSelectedDefaultKiosk();
            reloadContent();
        }
    }

    @Override
    public void onDestroyView() {
        if (tournesolFilterController != null) {
            tournesolFilterController.onDestroyView();
            tournesolFilterController = null;
        }
        super.onDestroyView();
    }

    private void ensureTournesolController(@NonNull final View rootView) {
        if (tournesolFilterController == null) {
            tournesolFilterController = new TournesolFilterController(
                    this, config -> applyTournesolFilters(config, true));
        }
        tournesolFilterController.init(rootView);
    }

    private void applyTournesolFilters(@NonNull final TournesolFilterConfig config,
                                       final boolean reload) {
        url = TournesolHelper.INSTANCE.buildTournesolUrl(config);
        if (!reload) {
            return;
        }
        currentInfo = null;
        currentNextPage = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        reloadContent();
    }

    private void hideTournesolHeader(@NonNull final View rootView) {
        final View headerContainer = rootView.findViewById(R.id.kiosk_header_container);
        if (headerContainer != null) {
            headerContainer.setVisibility(View.GONE);
        }
    }

    private void updateSelectedDefaultKiosk() {
        try {
            serviceId = ServiceHelper.getSelectedServiceId(requireContext());

            final KioskList kioskList = NewPipe.getService(serviceId).getKioskList();
            kioskId = kioskList.getDefaultKioskId();
            url = kioskList.getListLinkHandlerFactoryByType(kioskId).fromId(kioskId).getUrl();

            kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, requireContext());
            name = kioskTranslatedName;

            currentInfo = null;
            currentNextPage = null;
        } catch (final ExtractionException e) {
            showError(new ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading default kiosk for selected service"));
        }
    }
}
