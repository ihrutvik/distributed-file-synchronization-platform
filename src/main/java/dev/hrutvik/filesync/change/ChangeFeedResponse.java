package dev.hrutvik.filesync.change;
import java.util.List;
public record ChangeFeedResponse(List<ChangeItem> changes,long nextCursor,boolean hasMore) {}
