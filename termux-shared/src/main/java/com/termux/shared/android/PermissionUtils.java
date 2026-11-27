package com.termux.shared.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.termux.shared.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.errors.Error;
import com.termux.shared.errors.FunctionErrno;
import com.termux.shared.activity.ActivityUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PermissionUtils {

    public static final int REQUEST_GRANT_STORAGE_PERMISSION = 1000;

    public static final int REQUEST_DISABLE_BATTERY_OPTIMIZATIONS = 2000;
    public static final int REQUEST_GRANT_DISPLAY_OVER_OTHER_APPS_PERMISSION = 2001;

    private static final String LOG_TAG = "PermissionUtils";


    /**
     * Check if app has been granted the required permission.
     *
     * @param context The context for operations.
     * @param permission The {@link String} name for permission to check.
     * @return Returns {@code true} if permission is granted, otherwise {@code false}.
     */
    public static boolean checkPermission(@NonNull Context context, @NonNull String permission) {
        return checkPermissions(context, new String[]{permission});
    }

    /**
     * Check if app has been granted the required permissions.
     *
     * @param context The context for operations.
     * @param permissions The {@link String[]} names for permissions to check.
     * @return Returns {@code true} if permissions are granted, otherwise {@code false}.
     */
    public static boolean checkPermissions(@NonNull Context context, @NonNull String[] permissions) {
        // checkSelfPermission may return true for permissions not even requested
        List<String> permissionsNotRequested = getPermissionsNotRequested(context, permissions);
        if (permissionsNotRequested.size() > 0) {
            Logger.logError(LOG_TAG,
                context.getString(R.string.error_attempted_to_check_for_permissions_not_requested,
                    TextUtils.join(", ", permissionsNotRequested)));
            return false;
        }

        int result;
        for (String permission : permissions) {
            result = context.checkSelfPermission(permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }



    /**
     * Request user to grant required permissions to the app.
     *
     * @param context The Activity requesting permissions.
     * @param permission The {@link String} name for permission to request.
     * @param requestCode The request code to use while asking for permission. It must be `>=0` or
     *                    will fail silently and will log an exception.
     * @return Returns {@code true} if requesting the permission was successful, otherwise {@code false}.
     */
    public static boolean requestPermission(@NonNull Context context, @NonNull String permission,
                                            int requestCode) {
        return requestPermissions(context, new String[]{permission}, requestCode);
    }

    /**
     * Request user to grant required permissions to the app.
     *
     * @param context The Activity requesting permissions.
     * @param permissions The {@link String[]} names for permissions to request.
     * @param requestCode The request code to use while asking for permissions. It must be `>=0` or
     *                    will fail silently and will log an exception.
     * @return Returns {@code true} if requesting the permissions was successful, otherwise {@code false}.
     */
    public static boolean requestPermissions(@NonNull Context context, @NonNull String[] permissions,
                                             int requestCode) {
        List<String> permissionsNotRequested = getPermissionsNotRequested(context, permissions);
        if (permissionsNotRequested.size() > 0) {
            Logger.logErrorAndShowToast(context, LOG_TAG,
                context.getString(R.string.error_attempted_to_ask_for_permissions_not_requested,
                    TextUtils.join(", ", permissionsNotRequested)));
            return false;
        }

        for (String permission : permissions) {
            int result = context.checkSelfPermission(permission);
            // If at least one permission not granted
            if (result != PackageManager.PERMISSION_GRANTED) {
                Logger.logInfo(LOG_TAG, "Requesting Permissions: " + Arrays.toString(permissions));

                try {
                    if (context instanceof Activity)
                        ((Activity) context).requestPermissions(permissions, requestCode);
                    else {
                        Error.logErrorAndShowToast(context, LOG_TAG,
                            FunctionErrno.ERRNO_PARAMETER_NOT_INSTANCE_OF.getError("context", "requestPermissions", "Activity"));
                        return false;
                    }
                } catch (Exception e) {
                    String errmsg = context.getString(R.string.error_failed_to_request_permissions, requestCode, Arrays.toString(permissions));
                    Logger.logStackTraceWithMessage(LOG_TAG, errmsg, e);
                    Logger.showToast(context, errmsg + "\n" + e.getMessage(), true);
                    return false;
                }

                break;
            }
        }

        return true;
    }




    /**
     * Check if app has requested the required permission in the manifest.
     *
     * @param context The context for operations.
     * @param permission The {@link String} name for permission to check.
     * @return Returns {@code true} if permission has been requested, otherwise {@code false}.
     */
    public static boolean isPermissionRequested(@NonNull Context context, @NonNull String permission) {
        return getPermissionsNotRequested(context, new String[]{permission}).size() == 0;
    }

    /**
     * Check if app has requested the required permissions or not in the manifest.
     *
     * @param context The context for operations.
     * @param permissions The {@link String[]} names for permissions to check.
     * @return Returns {@link List<String>} of permissions that have not been requested. It will have
     * size 0 if all permissions have been requested.
     */
    @NonNull
    public static List<String> getPermissionsNotRequested(@NonNull Context context, @NonNull String[] permissions) {
        List<String> permissionsNotRequested = new ArrayList<>();
        Collections.addAll(permissionsNotRequested, permissions);

        PackageInfo packageInfo = PackageUtils.getPackageInfoForPackage(context, PackageManager.GET_PERMISSIONS);
        if (packageInfo == null) {
            return permissionsNotRequested;
        }

        // If no permissions are requested, then nothing to check
        if (packageInfo.requestedPermissions == null || packageInfo.requestedPermissions.length == 0)
            return permissionsNotRequested;

        List<String> requestedPermissionsList = Arrays.asList(packageInfo.requestedPermissions);
        for (String permission : permissions) {
            if (requestedPermissionsList.contains(permission)) {
                permissionsNotRequested.remove(permission);
            }
        }

        return permissionsNotRequested;
    }




    /**
     * Request access for the shell's public-storage links. Android can still grant legacy
     * storage access to the target-28 app. Scoped installs use Android's all-files settings.
     */
    public static boolean checkAndRequestLegacyOrManageExternalStoragePermission(
        @NonNull Context context, int requestCode, boolean showErrorMessage) {
        boolean legacy = Environment.isExternalStorageLegacy();
        if (Environment.isExternalStorageManager() || (legacy && checkPermissions(context,
            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}))) {
            return true;
        }

        String message = context.getString(R.string.msg_storage_permission_not_granted);
        Logger.logError(LOG_TAG, message);
        if (showErrorMessage) Logger.showToast(context, message, false);
        if (requestCode >= 0) {
            if (legacy) requestPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE, requestCode);
            else requestManageStorageExternalPermission(context, requestCode);
        }
        return false;
    }

    /**
     * Request user to grant {@link Manifest.permission#MANAGE_EXTERNAL_STORAGE} permission to the app.
     *
     * @param context The context for operations, like an {@link Activity} or {@link Service} context.
     *                It must be an instance of {@link Activity} if
     *                result is required via the Activity#onActivityResult() callback and
     *                {@code requestCode} is `>=0`.
     * @param requestCode The request code to use while asking for permission. It must be `>=0` if
     *                    result it required.
     * @return Returns the {@code error} if requesting the permission was not successful, otherwise {@code null}.
     */
    public static Error requestManageStorageExternalPermission(@NonNull Context context, int requestCode) {
        Logger.logInfo(LOG_TAG, "Requesting manage external storage permission");

        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.addCategory("android.intent.category.DEFAULT");
        intent.setData(Uri.parse("package:" + context.getPackageName()));

        // Flag must not be passed for activity contexts, otherwise onActivityResult() will not be called with permission grant result.
        // Flag must be passed for non-activity contexts like services, otherwise "Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag" exception will be raised.
        if (!(context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Error error;
        if (requestCode >=0)
            error = ActivityUtils.startActivityForResult(context, requestCode, intent, true, false);
        else
            error = ActivityUtils.startActivity(context, intent, true, false);

        // Use fallback if matching Activity did not exist for ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION.
        if (error != null) {
            intent = new Intent();
            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            if (requestCode >=0)
                return ActivityUtils.startActivityForResult(context, requestCode, intent);
            else
                return ActivityUtils.startActivity(context, intent);
        }

        return null;
    }

    /**
     * Check if {@link Manifest.permission#SYSTEM_ALERT_WINDOW} permission has been granted.
     *
     * @param context The context for operations.
     * @return Returns {@code true} if permission is granted, otherwise {@code false}.
     */
    public static boolean checkDisplayOverOtherAppsPermission(@NonNull Context context) {
        return Settings.canDrawOverlays(context);
    }

    /** Wrapper for {@link #requestDisplayOverOtherAppsPermission(Context, int)}. */
    public static Error requestDisplayOverOtherAppsPermission(@NonNull Context context) {
        return requestDisplayOverOtherAppsPermission(context, -1);
    }

    /**
     * Request user to grant {@link Manifest.permission#SYSTEM_ALERT_WINDOW} permission to the app.
     *
     * @param context The context for operations, like an {@link Activity} or {@link Service} context.
     *                It must be an instance of {@link Activity} if
     *                result is required via the Activity#onActivityResult() callback and
     *                {@code requestCode} is `>=0`.
     * @param requestCode The request code to use while asking for permission. It must be `>=0` if
     *                    result it required.
     * @return Returns the {@code error} if requesting the permission was not successful, otherwise {@code null}.
     */
    public static Error requestDisplayOverOtherAppsPermission(@NonNull Context context, int requestCode) {
        Logger.logInfo(LOG_TAG, "Requesting display over apps permission");

        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + context.getPackageName()));

        // Flag must not be passed for activity contexts, otherwise onActivityResult() will not be called with permission grant result.
        // Flag must be passed for non-activity contexts like services, otherwise "Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag" exception will be raised.
        if (!(context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (requestCode >=0)
            return ActivityUtils.startActivityForResult(context, requestCode, intent);
        else
            return ActivityUtils.startActivity(context, intent);
    }

    /**
     * Check whether Android allows the app to display overlays.
     *
     * @param context The context for operations.
     * @param logResults If it should be logged that permission has been granted or not.
     * @return Returns {@code true} if permission is granted, otherwise {@code false}.
     */
    public static boolean validateDisplayOverOtherAppsPermission(@NonNull Context context,
                                                                                 boolean logResults) {
        if (!checkDisplayOverOtherAppsPermission(context)) {
            if (logResults)
                Logger.logWarn(LOG_TAG, context.getPackageName() + " does not have Display over other apps (SYSTEM_ALERT_WINDOW) permission");
            return false;
        } else {
            if (logResults)
                Logger.logDebug(LOG_TAG, context.getPackageName() + " already has Display over other apps (SYSTEM_ALERT_WINDOW) permission");
            return true;
        }
    }





    /**
     * Check if {@link Manifest.permission#REQUEST_IGNORE_BATTERY_OPTIMIZATIONS} permission has been
     * granted.
     *
     * @param context The context for operations.
     * @return Returns {@code true} if permission is granted, otherwise {@code false}.
     */
    public static boolean checkIfBatteryOptimizationsDisabled(@NonNull Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /** Wrapper for {@link #requestDisableBatteryOptimizations(Context, int)}. */
    public static Error requestDisableBatteryOptimizations(@NonNull Context context) {
        return requestDisableBatteryOptimizations(context, -1);
    }

    /**
     * Request user to grant {@link Manifest.permission#REQUEST_IGNORE_BATTERY_OPTIMIZATIONS}
     * permission to the app.
     *
     * @param context The context for operations, like an {@link Activity} or {@link Service} context.
     *                It must be an instance of {@link Activity} if
     *                result is required via the Activity#onActivityResult() callback and
     *                {@code requestCode} is `>=0`.
     * @param requestCode The request code to use while asking for permission. It must be `>=0` if
     *                    result it required.
     * @return Returns the {@code error} if requesting the permission was not successful, otherwise {@code null}.
     */
    @SuppressLint("BatteryLife")
    public static Error requestDisableBatteryOptimizations(@NonNull Context context, int requestCode) {
        Logger.logInfo(LOG_TAG, "Requesting to disable battery optimizations");

        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));

        // Flag must not be passed for activity contexts, otherwise onActivityResult() will not be called with permission grant result.
        // Flag must be passed for non-activity contexts like services, otherwise "Calling startActivity() from outside of an Activity context requires the FLAG_ACTIVITY_NEW_TASK flag" exception will be raised.
        if (!(context instanceof Activity))
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (requestCode >=0)
            return ActivityUtils.startActivityForResult(context, requestCode, intent);
        else
            return ActivityUtils.startActivity(context, intent);
    }

}
