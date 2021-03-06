package com.ctrip.xpipe.redis.core.protocal.cmd.transaction;


import java.net.InetSocketAddress;

import org.junit.Assert;
import org.junit.Test;

import com.ctrip.xpipe.pool.XpipeNettyClientKeyedObjectPool;
import com.ctrip.xpipe.redis.core.AbstractRedisTest;

/**
 * manual test for local redis
 * @author wenchao.meng
 *
 * Dec 9, 2016
 */
public class TransactionalSlaveOfCommandTest extends AbstractRedisTest{
	
	private String ip = "localhost";
	
	private int port = 6379;
	
	private int testCount = 10;
	
	@Test
	public void testXslaveof() throws Exception{
		
		XpipeNettyClientKeyedObjectPool pool = getXpipeNettyClientKeyedObjectPool();
		
		for(int i=0; i < testCount; i++){
			
			logger.info(remarkableMessage("{}"), i);
			TransactionalSlaveOfCommand command = new TransactionalSlaveOfCommand(getXpipeNettyClientKeyedObjectPool().getKeyPool(new InetSocketAddress(ip, port)), ip, port, scheduled);
			
			Object []result = command.execute().get();
			logger.info("{}", (Object)result);
			
			Assert.assertEquals(0, pool.getObjectPool().getNumActive());
			Assert.assertEquals(1, pool.getObjectPool().getNumIdle());
		}
		
	}

	@Test
	public void testSaveof(){
		
	}

}
