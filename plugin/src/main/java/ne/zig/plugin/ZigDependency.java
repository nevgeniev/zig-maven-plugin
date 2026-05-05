package ne.zig.plugin;

public class ZigDependency {
    private String groupId;
    private String artifactId;
    private String version;
    private String type;
    private boolean unpack = false;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getType() {
        return type == null ? "tar.xz" : type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isUnpack() {
        return unpack;
    }

    public void setUnpack(boolean unpack) {
        this.unpack = unpack;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s:%s", groupId, artifactId, version, getType());
    }

    public String toDigestString(String hash) {
        return String.format("%s:%s:%s:%s:%b:%s", groupId, artifactId, version, getType(), unpack, hash);
    }

}
