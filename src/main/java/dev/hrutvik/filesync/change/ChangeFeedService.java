package dev.hrutvik.filesync.change;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;
@Service public class ChangeFeedService {
 private final ChangeFeedStore store; public ChangeFeedService(ChangeFeedStore store){this.store=store;}
 @Transactional(readOnly=true) public ChangeFeedResponse changes(String tenant,long after,int requestedLimit){int limit=normalizeLimit(requestedLimit);var rows=store.after(tenant,after,limit+1);boolean more=rows.size()>limit;var page=more?List.copyOf(rows.subList(0,limit)):List.copyOf(rows);long next=page.isEmpty()?after:page.get(page.size()-1).cursor();return new ChangeFeedResponse(page,next,more);}
 static int normalizeLimit(int limit){if(limit<1||limit>500)throw new IllegalArgumentException("limit must be between 1 and 500");return limit;}
}
