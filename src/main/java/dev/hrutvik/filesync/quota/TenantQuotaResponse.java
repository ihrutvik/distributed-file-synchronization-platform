package dev.hrutvik.filesync.quota;
public record TenantQuotaResponse(long usedBytes,long quotaBytes,long availableBytes,double utilizationPercent) {
 static TenantQuotaResponse from(TenantQuotaStore.State state){long available=Math.max(0,state.quotaBytes()-state.usedBytes());double percent=state.quotaBytes()==0?100.0:(state.usedBytes()*100.0/state.quotaBytes());return new TenantQuotaResponse(state.usedBytes(),state.quotaBytes(),available,percent);}
}
