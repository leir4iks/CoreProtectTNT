package com.leir4iks.coreprotecttnt;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record UpdateInfo(
        @SerializedName("latest_version") String latestVersion,
        @SerializedName("download_url") String downloadUrl,
        @SerializedName("changelog") List<String> changelog
) {}