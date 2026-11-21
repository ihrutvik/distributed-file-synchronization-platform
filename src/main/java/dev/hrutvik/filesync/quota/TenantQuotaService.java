package dev.hrutvik.filesync.quota;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class TenantQuotaService {
 private final TenantQuotaStore store; public TenantQuotaService(TenantQuotaStore store){this.store=store;}
 @Transactional public TenantQuotaResponse get(String tenant){return TenantQuotaResponse.from(store.get(tenant));}
}
