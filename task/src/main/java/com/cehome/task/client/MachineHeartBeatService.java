package com.cehome.task.client;

import com.cehome.task.TimeTaskClient;
import com.cehome.task.TimeTaskFactory;
import com.cehome.task.service.ConfigService;
import com.cehome.task.service.MachineBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 机器心跳服务
 * @author ruixiang.mrx
 * 1、 机器启动，第一次执行，加入启动map中
 * 2、（频率3秒）进行分布式加锁，加锁成功的可以进行状态判断。
 *         获取hashmap，判断时间，如果超过5秒则认为宕机 最终形成在线、离线、刚启动
 *         三钟列表；对于离线的转移、删除key；对于刚启动的恢复，并删除key。
 * 
 *         如果宕机，先判断一遍，然后则把宕机切任务切换到正常机器。
 * 
 */
public class MachineHeartBeatService extends MachineBaseService implements InitializingBean {
    protected static final Logger logger = LoggerFactory.getLogger(MachineHeartBeatService.class);
    private boolean inited=false;
	private long clusterHeartBeatInterval;
	private boolean useHostName=false;
	@Autowired
	protected ConfigService configService;
	@Autowired
	protected TimeTaskClient timeTaskClient;


	private int  APP_HEART_BEAT_SECONDS=3600;
	public String getAppName() {
		return appName;
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	private String appName;
	public MachineHeartBeatService(){
		super();

	}

	public long getClusterHeartBeatInterval() {
		return clusterHeartBeatInterval;
	}

	public void setClusterHeartBeatInterval(long clusterHeartBeatInterval) {
		this.clusterHeartBeatInterval = clusterHeartBeatInterval;
	}



	@Override
	public void afterPropertiesSet() throws Exception {
		//可能有多个context，会执行2次
		if(inited) return;
		logger.info("first run，add to start map. host=" + timeTaskClient.getLocalMachine());
		configService.hset(getClusterName() + KEY_MACHINES_START+appName, timeTaskClient.getLocalMachine(), "1");

		/*if(timeTaskClient.isUseHostName()){
			configService.hset(getClusterName() + KEY_HOST_IP, timeTaskFactory.getLocalHostName(), timeTaskFactory.getLocalIP());
		}*/

		inited=true;

		TimeTaskFactory.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {

				appHeartbeat();

			}
		}, 0,APP_HEART_BEAT_SECONDS*1000);


		TimeTaskFactory.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {

				machineHeartbeat();

			}
		}, 0,getClusterHeartBeatInterval());

	}
    /**
     * 机器心跳
     *
     * @throws Exception
     */
	//@Scheduled(fixedDelay = Constants.CLIENT_HEART_BEAT_INTERVAL)
	public void machineHeartbeat()   {
		try {
			logger.info("\r\n\r\n");
			logger.info("send heart, update online machine");
			configService.hset(getClusterName() + KEY_MACHINES + appName, timeTaskClient.getLocalMachine(), "" + configService.getTime());
			configService.expire(getClusterName() + KEY_MACHINES + appName, 3600 * 24 * 7);
		}catch (Exception e){
			logger.error("heart bean error",e);
		}

	}

	public void appHeartbeat()   {
		try {
			logger.info("\r\n\r\n");

			configService.sadd  (getClusterName() +KEY_APPS, appName);
			configService.expire(getClusterName() +KEY_APPS, APP_HEART_BEAT_SECONDS*3);
		}catch (Exception e){
			logger.error("app heartbeat error",e);
		}

	}

	 
}
