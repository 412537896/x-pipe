package com.ctrip.xpipe.redis.core.protocal.cmd;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

import com.ctrip.xpipe.api.endpoint.Endpoint;
import com.ctrip.xpipe.api.pool.SimpleObjectPool;
import com.ctrip.xpipe.exception.XpipeRuntimeException;
import com.ctrip.xpipe.netty.commands.NettyClient;
import com.ctrip.xpipe.redis.core.store.ReplicationStore;
import com.ctrip.xpipe.redis.core.store.ReplicationStoreManager;
import com.ctrip.xpipe.redis.core.store.ReplicationStoreMeta;

/**
 * @author wenchao.meng
 *
 * 2016年3月24日 下午2:24:38
 */
public class DefaultPsync extends AbstractReplicationStorePsync{
	
	private ReplicationStoreManager replicationStoreManager;
	
	private Endpoint masterEndPoint;
	
	
	public DefaultPsync(SimpleObjectPool<NettyClient> clientPool, 
			Endpoint masterEndPoint, ReplicationStoreManager replicationStoreManager, ScheduledExecutorService scheduled) {
		super(clientPool, true, scheduled);
		this.masterEndPoint = masterEndPoint;
		this.replicationStoreManager = replicationStoreManager;
		currentReplicationStore = getCurrentReplicationStore();
	}
	
	@Override		
	protected ReplicationStore getCurrentReplicationStore() {
		
		try {
			return replicationStoreManager.createIfNotExist();
		} catch (IOException e) {
			logger.error("[doRequest]" + this + replicationStoreManager, e);
			throw new XpipeRuntimeException("[doRequest]getReplicationStore failed." + replicationStoreManager, e);
		}
	}


	@Override
	public String toString() {
		return getName() + "->"  + masterEndPoint;
	}
	
	@Override
	protected void doWhenFullSyncToNonFreshReplicationStore(String masterRunid) throws IOException {
		
		ReplicationStore oldStore = currentReplicationStore;
		long newKeeperBeginOffset = ReplicationStoreMeta.DEFAULT_KEEPER_BEGIN_OFFSET;
		if(oldStore != null){
			try {
				logger.info("[doWhenFullSyncToNonFreshReplicationStore][full sync][replication store out of time, destroy]{}, {}", this, currentReplicationStore);
				newKeeperBeginOffset = oldStore.nextNonOverlappingKeeperBeginOffset();
				oldStore.close();
			} catch (Exception e) {
				logger.error("[handleRedisReponse]" + oldStore, e);
			}
			notifyReFullSync();
		}
		logger.info("[doWhenFullSyncToNonFreshReplicationStore][set keepermeta]{}, {}", masterRunid, newKeeperBeginOffset);
		currentReplicationStore = createReplicationStore(masterRunid, newKeeperBeginOffset);
	}
	
	private ReplicationStore createReplicationStore(String masterRunid, long keeperBeginOffset) {
		
		try {
			return replicationStoreManager.create(masterRunid, keeperBeginOffset);
		} catch (IOException e) {
			throw new XpipeRuntimeException("[createNewReplicationStore]" + replicationStoreManager, e);
		}
	}
}
