package com.cmbchina.schedule;

import com.cmbchina.MessageQueue;
import com.cmbchina.Utils;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * Created by Andrew on 30/12/2016.
 */
public class MessageSchedule {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ConcurrentHashMap<String,MessageQueue> messageQueueConcurrentHashMap;
    private Scheduler scheduler = null;
    private String tabschema;
    private JdbcTemplate jdbcTemplate;

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getTabschema() {
        return tabschema;
    }

    public void setTabschema(String tabschema) {
        this.tabschema = tabschema;
    }

    public MessageSchedule() {
        logger.info("Start Init MessageSchedule");
        StdSchedulerFactory factory = new StdSchedulerFactory();
        try {
            factory.initialize(Utils.getQuartzConf());
            this.messageQueueConcurrentHashMap=new ConcurrentHashMap<>();
            scheduler = factory.getScheduler();
            scheduler.start();
        } catch (SchedulerException e) {
            logger.error(e.getMessage().toString());
        }
    }

    public void start(){
        for(Map<String,Object> tabname:getTabName()){
            MessageQueue messageQueue = new MessageQueue(this.jdbcTemplate,tabschema,tabname.get("TABNAME").toString());
            this.messageQueueConcurrentHashMap.put(tabname.get("TABNAME").toString().trim(),messageQueue);
            JobDetail jobDetail = newJob(MessageSaveJob.class)
                    .withIdentity(tabname.get("TABNAME").toString())
                    .build();
            jobDetail.getJobDataMap().put(MessageQueue.class.toString(),messageQueue);
            jobDetail.getJobDataMap().put(JdbcTemplate.class.toString(),jdbcTemplate);
            logger.info("Build Job "+ jobDetail.getKey().getName());

            Trigger trigger = newTrigger()
                    .withIdentity(tabname.get("TABNAME").toString())
                    .startNow()
                    .withSchedule(simpleSchedule()
                            .withIntervalInSeconds(1)
                            .repeatForever())
                    .build();
            logger.info("Build Trigger " + trigger.getKey().getName());
            try {
                this.scheduler.scheduleJob(jobDetail,trigger);
            } catch (SchedulerException e) {
                logger.error(e.getStackTrace().toString());
            }
        }
    }

    public void stop(){

    }

    private List<Map<String, Object>> getTabName(){
        String sql="SELECT TABNAME FROM SYSCAT.TABLES WHERE TABSCHEMA=? AND TYPE='T'";
        return jdbcTemplate.queryForList(sql,this.tabschema.toUpperCase());
    }

    public void processMessage(String msg,String host,int port){
        String[] msgs=msg.split("\\\\\\\\r\\\\\\\\n");
        for(String message:msgs){
            Object [] messages = message.split(",");
            if(this.messageQueueConcurrentHashMap.containsKey(messages[0])){
                this.messageQueueConcurrentHashMap.get(messages[0]).push(messages,host,port);
            }
        }
    }
}