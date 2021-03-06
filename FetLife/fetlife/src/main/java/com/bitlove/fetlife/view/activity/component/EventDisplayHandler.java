package com.bitlove.fetlife.view.activity.component;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;

import com.bitlove.fetlife.R;
import com.bitlove.fetlife.event.AuthenticationFailedEvent;
import com.bitlove.fetlife.event.LatestReleaseEvent;
import com.bitlove.fetlife.event.PictureUploadFailedEvent;
import com.bitlove.fetlife.event.PictureUploadFinishedEvent;
import com.bitlove.fetlife.event.PictureUploadStartedEvent;
import com.bitlove.fetlife.event.ServiceCallCancelEvent;
import com.bitlove.fetlife.event.ServiceCallCancelRequestEvent;
import com.bitlove.fetlife.event.ServiceCallFailedEvent;
import com.bitlove.fetlife.event.ServiceCallFinishedEvent;
import com.bitlove.fetlife.event.ServiceCallStartedEvent;
import com.bitlove.fetlife.event.VideoChunkUploadCancelEvent;
import com.bitlove.fetlife.event.VideoChunkUploadCancelRequestEvent;
import com.bitlove.fetlife.event.VideoChunkUploadFailedEvent;
import com.bitlove.fetlife.event.VideoChunkUploadFinishedEvent;
import com.bitlove.fetlife.event.VideoChunkUploadStartedEvent;
import com.bitlove.fetlife.event.VideoUploadFailedEvent;
import com.bitlove.fetlife.model.pojos.NotificationHistoryItem;
import com.bitlove.fetlife.model.pojos.github.Release;
import com.bitlove.fetlife.model.service.FetLifeApiIntentService;
import com.bitlove.fetlife.model.service.ServiceCallCancelReceiver;
import com.bitlove.fetlife.util.AppUtil;
import com.bitlove.fetlife.util.VersionUtil;
import com.bitlove.fetlife.view.activity.BaseActivity;
import com.bitlove.fetlife.view.activity.resource.ResourceActivity;

import java.util.HashMap;
import java.util.Map;

public class EventDisplayHandler {

    private static int PICTURE_UPLOAD_NOTIFICATION_ID = 42;
    private static int VIDEO_UPLOAD_NOTIFICATION_ID = 1042;
    private static int RELEASE_NOTIFICATION_ID = 10042;

    private static Map<String,Integer> notificationIdMap = new HashMap<>();

    public void onAuthenticationFailed(BaseActivity baseActivity, AuthenticationFailedEvent authenticationFailedEvent) {
        baseActivity.showToast(baseActivity.getString(R.string.error_authentication_failed));
    }

    public void onServiceCallFailed(BaseActivity baseActivity, ServiceCallFailedEvent serviceCallFailedEvent) {
        if (serviceCallFailedEvent instanceof PictureUploadFailedEvent
                || FetLifeApiIntentService.ACTION_APICALL_UPLOAD_PICTURE.equals(serviceCallFailedEvent.getServiceCallAction())) {
            baseActivity.showToast(baseActivity.getString(R.string.message_image_upload_failed));
            showMessageNotification(baseActivity, PICTURE_UPLOAD_NOTIFICATION_ID++, baseActivity.getString(R.string.notification_picture_upload_title), baseActivity.getString(R.string.message_image_upload_failed), null);
        } else if (serviceCallFailedEvent instanceof VideoUploadFailedEvent) {
            baseActivity.showToast(baseActivity.getString(R.string.message_video_upload_failed_file_size, FetLifeApiIntentService.MAX_VIDEO_FILE_SIZE));
            showMessageNotification(baseActivity, VIDEO_UPLOAD_NOTIFICATION_ID++, baseActivity.getString(R.string.notification_video_upload_title), baseActivity.getString(R.string.message_video_upload_failed_file_size, FetLifeApiIntentService.MAX_VIDEO_FILE_SIZE), null);
        } else if (serviceCallFailedEvent instanceof VideoChunkUploadFailedEvent) {
            VideoChunkUploadFailedEvent videoChunkUploadFailedEvent = (VideoChunkUploadFailedEvent) serviceCallFailedEvent;
            int notificationId = getNotificationIdFromMediaId(videoChunkUploadFailedEvent.getVideoId());
            dismissNotification(baseActivity, notificationId);
            String message = videoChunkUploadFailedEvent.isCancelled() ? baseActivity.getString(R.string.message_video_upload_cancelled) : baseActivity.getString(R.string.message_video_upload_failed);
            showMessageNotification(baseActivity, notificationId, baseActivity.getString(R.string.notification_video_upload_title), message, null);
            baseActivity.showToast(message);
        } else if (FetLifeApiIntentService.ACTION_APICALL_UPLOAD_VIDEO.equals(serviceCallFailedEvent.getServiceCallAction())) {
            baseActivity.showToast(baseActivity.getString(R.string.message_video_upload_failed));
            showMessageNotification(baseActivity, VIDEO_UPLOAD_NOTIFICATION_ID++, baseActivity.getString(R.string.notification_video_upload_title), baseActivity.getString(R.string.message_video_upload_failed), null);
        } else {
            if (serviceCallFailedEvent.isServerConnectionFailed()) {
                baseActivity.showToast(baseActivity.getResources().getString(R.string.error_connection_failed));
            } else {
                baseActivity.showToast(baseActivity.getResources().getString(R.string.error_apicall_failed));
            }
        }
    }

    public void onServiceCallFinished(BaseActivity baseActivity, ServiceCallFinishedEvent serviceCallFinishedEvent) {
        if (serviceCallFinishedEvent instanceof PictureUploadFinishedEvent) {
            int notificationId = getNotificationIdFromMediaId(((PictureUploadFinishedEvent)serviceCallFinishedEvent).getPictureId());
            dismissNotification(baseActivity, notificationId);
            showMessageNotification(baseActivity, notificationId, baseActivity.getString(R.string.notification_picture_upload_title), baseActivity.getString(R.string.message_image_upload_finished), null);
            baseActivity.showToast(baseActivity.getString(R.string.message_image_upload_finished));
        } else if (serviceCallFinishedEvent instanceof VideoChunkUploadFinishedEvent) {
            VideoChunkUploadFinishedEvent videoChunkUploadFinishedEvent = (VideoChunkUploadFinishedEvent) serviceCallFinishedEvent;
            if (videoChunkUploadFinishedEvent.getChunk() == videoChunkUploadFinishedEvent.getChunkCount()) {
                int notificationId = getNotificationIdFromMediaId(((VideoChunkUploadFinishedEvent)serviceCallFinishedEvent).getVideoId());
                dismissNotification(baseActivity, notificationId);
                showMessageNotification(baseActivity, notificationId, baseActivity.getString(R.string.notification_video_upload_title), baseActivity.getString(R.string.message_video_upload_finished), null);
                baseActivity.showToast(baseActivity.getString(R.string.message_video_upload_finished));
            }
        }
    }

    public void onServiceCallStarted(BaseActivity baseActivity, ServiceCallStartedEvent serviceCallStartedEvent) {
        if (serviceCallStartedEvent instanceof PictureUploadStartedEvent) {
            notificationIdMap.put(((PictureUploadStartedEvent)serviceCallStartedEvent).getPictureId(),PICTURE_UPLOAD_NOTIFICATION_ID);
            showProgressNotification(baseActivity, PICTURE_UPLOAD_NOTIFICATION_ID++, baseActivity.getString(R.string.notification_picture_upload_title), baseActivity.getString(R.string.notification_media_upload_text_inprogress), 0, 0, null);
            baseActivity.showToast(baseActivity.getString(R.string.message_image_upload_started));
        } else if (serviceCallStartedEvent instanceof VideoChunkUploadStartedEvent) {
            VideoChunkUploadStartedEvent videoChunkUploadStartedEvent = (VideoChunkUploadStartedEvent) serviceCallStartedEvent;
            if (videoChunkUploadStartedEvent.getChunk() == 1 && videoChunkUploadStartedEvent.getRetry() == 0) {
                notificationIdMap.put(videoChunkUploadStartedEvent.getVideoId(),VIDEO_UPLOAD_NOTIFICATION_ID);
                showProgressNotification(baseActivity, VIDEO_UPLOAD_NOTIFICATION_ID, baseActivity.getString(R.string.notification_video_upload_title), baseActivity.getString(R.string.notification_media_upload_text_inprogress, 0, videoChunkUploadStartedEvent.getVideoSize()), 0, 0, ServiceCallCancelReceiver.createVideoCancelPendingIntent(baseActivity, VIDEO_UPLOAD_NOTIFICATION_ID, videoChunkUploadStartedEvent.getVideoId()));
                VIDEO_UPLOAD_NOTIFICATION_ID++;
                baseActivity.showToast(baseActivity.getString(R.string.message_video_upload_started));
            } else if (videoChunkUploadStartedEvent.getRetry() == 0){
                int notificationId = getNotificationIdFromMediaId(videoChunkUploadStartedEvent.getVideoId());
                PendingIntent cancelIntent = videoChunkUploadStartedEvent.getChunk() * FetLifeApiIntentService.VIDEO_UPLOAD_CHUNK_SIZE_MBYTES >= videoChunkUploadStartedEvent.getVideoSize() ? null : ServiceCallCancelReceiver.createVideoCancelPendingIntent(baseActivity, notificationId, videoChunkUploadStartedEvent.getVideoId());
                showProgressNotification(baseActivity, notificationId, baseActivity.getString(R.string.notification_video_upload_title), baseActivity.getString(R.string.notification_media_upload_text_inprogress, (videoChunkUploadStartedEvent.getChunk()-1) * FetLifeApiIntentService.VIDEO_UPLOAD_CHUNK_SIZE_MBYTES, videoChunkUploadStartedEvent.getVideoSize()), 0, 0, cancelIntent);
            }
        }
    }

    public void onServiceCallCancelRequested(BaseActivity baseActivity, ServiceCallCancelRequestEvent serviceCallCancelRequestEvent) {
        if (serviceCallCancelRequestEvent instanceof VideoChunkUploadCancelRequestEvent) {
            VideoChunkUploadCancelRequestEvent videoChunkUploadCancelRequestEvent = (VideoChunkUploadCancelRequestEvent) serviceCallCancelRequestEvent;
            showProgressNotification(baseActivity, getNotificationIdFromMediaId(videoChunkUploadCancelRequestEvent.getVideoId()), baseActivity.getString(R.string.notification_video_upload_title), baseActivity.getString(R.string.notification_media_upload_text_being_cancelled), 0, 0, null);
            baseActivity.showToast(baseActivity.getString(R.string.message_video_upload_being_cancelled));
        }
    }

    public void onServiceCallCancelProcessed(BaseActivity baseActivity, ServiceCallCancelEvent serviceCallCancelEvent) {
        if (serviceCallCancelEvent instanceof VideoChunkUploadCancelEvent) {
            VideoChunkUploadCancelEvent videoChunkUploadCancelEvent = (VideoChunkUploadCancelEvent) serviceCallCancelEvent;
            if (videoChunkUploadCancelEvent.isCancelSucceed()) {
                int notificationId = getNotificationIdFromMediaId(((VideoChunkUploadCancelEvent)serviceCallCancelEvent).getVideoId());
                dismissNotification(baseActivity, notificationId);
                showMessageNotification(baseActivity, notificationId, baseActivity.getString(R.string.notification_video_upload_title), baseActivity.getString(R.string.message_video_upload_cancelled), null);
                baseActivity.showToast(baseActivity.getString(R.string.message_video_upload_cancelled));
            } else {
                baseActivity.showToast(baseActivity.getString(R.string.message_video_upload_cancel_failed));
            }
        }
    }

    public void onLatestReleaseChecked(BaseActivity baseActivity, LatestReleaseEvent latestReleaseEvent) {
        Release latestRelease = latestReleaseEvent.getLatestRelease();
        Release latestPreRelease = latestReleaseEvent.getLatestPreRelease();
        if (VersionUtil.toBeNotified(baseActivity, latestRelease)) {
            notifyAboutNewRelease(baseActivity, latestRelease);
        }
        if (VersionUtil.toBeNotified(baseActivity, latestPreRelease)) {
            notifyAboutNewRelease(baseActivity, latestPreRelease);
        }
    }

    private void notifyAboutNewRelease(BaseActivity baseActivity, Release release) {
        String header = baseActivity.getString(release.isPrerelease() ? R.string.notification_title_new_prerelease : R.string.notification_title_new_release);
        String message = baseActivity.getString(release.isPrerelease() ? R.string.notification_text_new_prerelease : R.string.notification_text_new_release,release.getTag());
        String url = release.getReleaseUrl();

        NotificationHistoryItem notificationHistoryItem = new NotificationHistoryItem();
        notificationHistoryItem.setTimeStamp(System.currentTimeMillis());
        notificationHistoryItem.setDisplayHeader(header);
        notificationHistoryItem.setDisplayMessage(message);
        notificationHistoryItem.setLaunchUrl(url);
        notificationHistoryItem.save();

        Intent notificationIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        PendingIntent pendingIntent = PendingIntent.getActivity(baseActivity, 0, notificationIntent, 0);
        showMessageNotification(baseActivity,RELEASE_NOTIFICATION_ID,header,message,pendingIntent);

        baseActivity.showToast(baseActivity.getString(release.isPrerelease() ? R.string.notification_toast_new_prerelease : R.string.notification_toast_new_release));
    }

    private int getNotificationIdFromMediaId(String mediaId) {
        int notificationId = notificationIdMap.get(mediaId);
        return notificationId;
    }

    private void showMessageNotification(BaseActivity baseActivity, int notificationId, String title, String text, PendingIntent pendingIntent) {
        NotificationManager notifyManager = (NotificationManager) baseActivity.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(baseActivity);

        builder.setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setSmallIcon(AppUtil.getAppIconResourceUrl(baseActivity.getFetLifeApplication(), true));
        notifyManager.notify(notificationId, builder.build());
    }

    private void showProgressNotification(BaseActivity baseActivity, int notificationId, String title, String text, int progress, int maxProgress, PendingIntent cancelIntent) {
        NotificationManager notifyManager = (NotificationManager) baseActivity.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(baseActivity);
        builder.setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(false)
                .setSmallIcon(AppUtil.getAppIconResourceUrl(baseActivity.getFetLifeApplication(), true));

        if (cancelIntent != null) {
            //TODO(VID): Add correct icon and text
            builder.addAction(android.R.drawable.ic_menu_delete, baseActivity.getString(android.R.string.cancel), cancelIntent);
        }

        if (maxProgress > 0) {
            builder.setProgress(maxProgress, progress, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        notifyManager.notify(notificationId, builder.build());
    }

    private void dismissNotification(BaseActivity baseActivity, int notificationId) {
        NotificationManager notifyManager = (NotificationManager) baseActivity.getSystemService(Context.NOTIFICATION_SERVICE);
        notifyManager.cancel(notificationId);
    }
}
