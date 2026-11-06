package org.jellyfin.mobile.nflix.butler;

import org.jellyfin.mobile.nflix.api.ApiClient;
import org.jellyfin.mobile.nflix.api.models.NflixButlerStatus;
import org.jellyfin.mobile.nflix.api.services.NflixButlerApiService;
import org.jellyfin.mobile.nflix.utils.UserManager;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class NflixButlerService {
    private final NflixButlerApiService apiService;
    private final UserManager userManager;

    public NflixButlerService(ApiClient apiClient, UserManager userManager) {
        this.apiService = new NflixButlerApiService(apiClient);
        this.userManager = userManager;
    }

    public Single<Boolean> isAdmin() {
        return apiService.isAdmin().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(response -> response != null && response);
    }

    public Single<NflixButlerStatus> getStatus() {
        return apiService.getStatus().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Single<Void> runNow() {
        return apiService.runNow().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Single<Void> fixItem(String itemId) {
        return apiService.fixItem(itemId).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Single<Void> refreshLibrary(String libraryId) {
        return apiService.refreshLibrary(libraryId).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}
