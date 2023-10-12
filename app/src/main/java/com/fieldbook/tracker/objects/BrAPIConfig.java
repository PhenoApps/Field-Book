package com.fieldbook.tracker.objects;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class BrAPIConfig {
    private String url;
    private String name;
    @SerializedName("v")
    private String version;
    @SerializedName("ps")
    private int pageSize;
    @SerializedName("cs")
    private int chunkSize;
    @SerializedName("st")
    private int serverTimeoutMilli;
    @SerializedName("flow")
    private String authFlow;
    @SerializedName("oidc")
    private String oidcUrl;
    @SerializedName("cat")
    private String catDisplay;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getServerTimeoutMilli() {
        return serverTimeoutMilli;
    }

    public void setServerTimeoutMilli(int serverTimeoutMilli) {
        this.serverTimeoutMilli = serverTimeoutMilli;
    }

    public String getAuthFlow() {
        return authFlow;
    }

    public void setAuthFlow(String authFlow) {
        this.authFlow = authFlow;
    }

    public String getOidcUrl() {
        return oidcUrl;
    }

    public void setOidcUrl(String oidcUrl) {
        this.oidcUrl = oidcUrl;
    }

    public String getCatDisplay() {
        return catDisplay;
    }

    public void setCatDisplay(String catDisplay) {
        this.catDisplay = catDisplay;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BrAPIConfig that = (BrAPIConfig) o;
        return pageSize == that.pageSize && chunkSize == that.chunkSize && serverTimeoutMilli == that.serverTimeoutMilli && url.equals(that.url) && name.equals(that.name) && version.equals(that.version) && authFlow.equals(that.authFlow) && oidcUrl.equals(
                that.oidcUrl) && catDisplay.equals(that.catDisplay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, name, version, pageSize, chunkSize, serverTimeoutMilli, authFlow, oidcUrl, catDisplay);
    }
}
