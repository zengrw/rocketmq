/**
 * Copyright (C) 2010-2013 Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.rocketmq.tools.command.consumer;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.alibaba.rocketmq.common.MQVersion;
import com.alibaba.rocketmq.common.MixAll;
import com.alibaba.rocketmq.common.UtilALl;
import com.alibaba.rocketmq.common.admin.ConsumeStats;
import com.alibaba.rocketmq.common.admin.OffsetWrapper;
import com.alibaba.rocketmq.common.message.MessageQueue;
import com.alibaba.rocketmq.common.protocol.body.ConsumerConnection;
import com.alibaba.rocketmq.common.protocol.body.TopicList;
import com.alibaba.rocketmq.common.protocol.heartbeat.ConsumeType;
import com.alibaba.rocketmq.common.protocol.heartbeat.MessageModel;
import com.alibaba.rocketmq.tools.admin.DefaultMQAdminExt;
import com.alibaba.rocketmq.tools.command.SubCommand;


/**
 * 查看订阅组消费状态，消费进度
 * 
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-8-11
 */
public class ConsumerProgressSubCommand implements SubCommand {

    @Override
    public String commandName() {
        return "consumerProgress";
    }


    @Override
    public String commandDesc() {
        return "Query consumers's progress, speed";
    }


    @Override
    public Options buildCommandlineOptions(Options options) {
        Option opt = new Option("g", "groupName", true, "consumer group name");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }


    @Override
    public void execute(CommandLine commandLine, Options options) {
        DefaultMQAdminExt defaultMQAdminExt = new DefaultMQAdminExt();

        defaultMQAdminExt.setInstanceName(Long.toString(System.currentTimeMillis()));

        try {
            defaultMQAdminExt.start();

            // 查询特定consumer
            if (commandLine.hasOption('g')) {
                String consumerGroup = commandLine.getOptionValue('g').trim();
                ConsumeStats consumeStats = defaultMQAdminExt.examineConsumeStats(consumerGroup);

                List<MessageQueue> mqList = new LinkedList<MessageQueue>();
                mqList.addAll(consumeStats.getOffsetTable().keySet());
                Collections.sort(mqList);

                System.out.printf("%-32s  %-32s  %-4s  %-20s  %-20s  %s\n",//
                    "#Topic",//
                    "#Broker Name",//
                    "#QID",//
                    "#Broker Offset",//
                    "#Consumer Offset",//
                    "#Diff" //
                );

                long diffTotal = 0L;

                for (MessageQueue mq : mqList) {
                    OffsetWrapper offsetWrapper = consumeStats.getOffsetTable().get(mq);

                    long diff = offsetWrapper.getBrokerOffset() - offsetWrapper.getConsumerOffset();
                    diffTotal += diff;

                    System.out.printf("%-32s  %-32s  %-4d  %-20d  %-20d  %d\n",//
                        UtilALl.frontStringAtLeast(mq.getTopic(), 32),//
                        UtilALl.frontStringAtLeast(mq.getBrokerName(), 32),//
                        mq.getQueueId(),//
                        offsetWrapper.getBrokerOffset(),//
                        offsetWrapper.getConsumerOffset(),//
                        diff //
                        );
                }

                System.out.println("");
                System.out.printf("Consume TPS: %d\n", consumeStats.getConsumeTps());
                System.out.printf("Diff Total: %d\n", diffTotal);
            }
            // 查询全部
            else {
                System.out.printf("%-32s  %-6s  %-24s %-4s  %-20s  %-7s  %s\n",//
                    "#Group",//
                    "#Count",//
                    "#Version",//
                    "#Type",//
                    "#Model",//
                    "#TPS",//
                    "#Diff Total"//
                );

                List<GroupConsumeInfo> groupConsumeInfoList = new LinkedList<GroupConsumeInfo>();

                TopicList topicList = defaultMQAdminExt.fetchAllTopicList();
                for (String topic : topicList.getTopicList()) {
                    if (topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)) {
                        String consumerGroup = topic.substring(MixAll.RETRY_GROUP_TOPIC_PREFIX.length());

                        try {
                            ConsumerConnection cc = null;
                            try {
                                cc = defaultMQAdminExt.examineConsumerConnectionInfo(consumerGroup);
                            }
                            catch (Exception e) {
                            }

                            ConsumeStats consumeStats = defaultMQAdminExt.examineConsumeStats(consumerGroup);

                            GroupConsumeInfo groupConsumeInfo = new GroupConsumeInfo();

                            groupConsumeInfo.setGroup(consumerGroup);

                            groupConsumeInfo.setConsumeTps((int) consumeStats.getConsumeTps());

                            groupConsumeInfo.setDiffTotal(consumeStats.computeTotalDiff());

                            if (cc != null) {
                                groupConsumeInfo.setCount(cc.getConnectionSet().size());
                                groupConsumeInfo.setMessageModel(cc.getMessageModel());
                                groupConsumeInfo.setConsumeType(cc.getConsumeType());
                                groupConsumeInfo.setVersion(cc.computeMinVersion());
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                Collections.sort(groupConsumeInfoList);

                for (GroupConsumeInfo info : groupConsumeInfoList) {
                    System.out.printf("%-32s  %-6d  %-24s %-4s  %-20s  %-7d  %d\n",//
                        info.getGroup(),//
                        info.getCount(),//
                        info.versionDesc(),//
                        info.consumeTypeDesc(),//
                        info.messageModelDesc(),//
                        info.getConsumeTps(),//
                        info.getDiffTotal()//
                        );
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            defaultMQAdminExt.shutdown();
        }
    }
}


class GroupConsumeInfo implements Comparable<GroupConsumeInfo> {
    private String group;
    private int version;
    private int count;
    private ConsumeType consumeType;
    private MessageModel messageModel;
    private int consumeTps;
    private long diffTotal;


    public String getGroup() {
        return group;
    }


    public String consumeTypeDesc() {
        if (this.count != 0) {
            return this.getConsumeType() == ConsumeType.CONSUME_ACTIVELY ? "PULL" : "PUSH";
        }
        return "";
    }


    public String messageModelDesc() {
        if (this.count != 0) {
            return this.getMessageModel().toString();
        }
        return "";
    }


    public String versionDesc() {
        if (this.count != 0) {
            return MQVersion.getVersionDesc(this.version);
        }
        return "";
    }


    public void setGroup(String group) {
        this.group = group;
    }


    public int getCount() {
        return count;
    }


    public void setCount(int count) {
        this.count = count;
    }


    public ConsumeType getConsumeType() {
        return consumeType;
    }


    public void setConsumeType(ConsumeType consumeType) {
        this.consumeType = consumeType;
    }


    public MessageModel getMessageModel() {
        return messageModel;
    }


    public void setMessageModel(MessageModel messageModel) {
        this.messageModel = messageModel;
    }


    public long getDiffTotal() {
        return diffTotal;
    }


    public void setDiffTotal(long diffTotal) {
        this.diffTotal = diffTotal;
    }


    @Override
    public int compareTo(GroupConsumeInfo o) {
        return (int) (diffTotal - o.diffTotal);
    }


    public int getConsumeTps() {
        return consumeTps;
    }


    public void setConsumeTps(int consumeTps) {
        this.consumeTps = consumeTps;
    }


    public int getVersion() {
        return version;
    }


    public void setVersion(int version) {
        this.version = version;
    }
}
