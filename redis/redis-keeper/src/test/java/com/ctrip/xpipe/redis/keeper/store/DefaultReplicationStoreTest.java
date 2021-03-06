package com.ctrip.xpipe.redis.keeper.store;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ctrip.xpipe.concurrent.AbstractExceptionLogTask;
import com.ctrip.xpipe.netty.filechannel.ReferenceFileRegion;
import com.ctrip.xpipe.redis.core.protocal.protocal.EofType;
import com.ctrip.xpipe.redis.core.protocal.protocal.LenEofType;
import com.ctrip.xpipe.redis.core.store.FullSyncListener;
import com.ctrip.xpipe.redis.core.store.RdbStore;
import com.ctrip.xpipe.redis.keeper.AbstractRedisKeeperTest;
import com.ctrip.xpipe.redis.keeper.config.DefaultKeeperConfig;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;

public class DefaultReplicationStoreTest extends AbstractRedisKeeperTest{

	private File baseDir;
	
	private DefaultReplicationStore store; 

	@Before
	public void beforeDefaultReplicationStoreTest() throws IOException{
		baseDir = new File(getTestFileDir());
		store = new DefaultReplicationStore(baseDir, new DefaultKeeperConfig(), randomKeeperRunid(), createkeeperMonitorManager());
		store.getMetaStore().becomeActive();
	}
	
	@Test
	public void testReadWhileDestroy() throws Exception{

		int dataLen = 1000;
		RdbStore rdbStore = store.beginRdb(randomKeeperRunid(), -1, new LenEofType(dataLen));
		
		rdbStore.writeRdb(Unpooled.wrappedBuffer(randomString(dataLen).getBytes()));
		rdbStore.endRdb();
		
		CountDownLatch latch  = new CountDownLatch(2);
		AtomicBoolean result = new AtomicBoolean(true);
		
		executors.execute(new AbstractExceptionLogTask() {
			
			@Override
			protected void doRun() throws Exception {
				
				try{
					sleep(2);
					store.close();
					store.destroy();
				}finally{
					latch.countDown();
				}
			}
		});
		
	
		executors.execute(new AbstractExceptionLogTask() {
			
			@Override
			protected void doRun() throws Exception {
				
				try{
					store.fullSyncIfPossible(new FullSyncListener() {
						
						@Override
						public ChannelFuture onCommand(ReferenceFileRegion referenceFileRegion) {
							
							return null;
						}
						
						@Override
						public void beforeCommand() {
							
						}
						
						@Override
						public void setRdbFileInfo(EofType eofType, long rdbFileKeeperOffset) {
							
						}
						
						@Override
						public void onFileData(ReferenceFileRegion referenceFileRegion) throws IOException {
							sleep(10);
						}
						
						@Override
						public boolean isOpen() {
							return true;
						}
						
						@Override
						public void exception(Exception e) {
							logger.info("[exception][fail]" + e.getMessage());
							result.set(false);
						}
						
						@Override
						public void beforeFileData() {
							
						}
					});
				}catch(Exception e){
					logger.info("[exception][fail]" + e.getMessage());
					result.set(false);
				}finally{
					latch.countDown();
				}
			}
		});
		
		
		latch.await(100, TimeUnit.MILLISECONDS);
		Assert.assertFalse(result.get());
	}

	
	@Test
	public void testReadWrite() throws Exception {
		
		StringBuffer exp = new StringBuffer();

		int cmdCount = 4;
		int cmdLen = 10;

		store.beginRdb("master", -1, new LenEofType(-1));

		for (int j = 0; j < cmdCount; j++) {
			ByteBuf buf = Unpooled.buffer();
			String cmd = UUID.randomUUID().toString().substring(0, cmdLen);
			exp.append(cmd);
			buf.writeBytes(cmd.getBytes());
			store.getCommandStore().appendCommands(buf);
		}
		String result = readCommandFileTilEnd(store);
		assertEquals(exp.toString(), result);
		store.close();
	}
	
	
}
