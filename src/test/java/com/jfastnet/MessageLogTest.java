/*******************************************************************************
 * Copyright 2015 Klaus Pfeiffer <klaus@allpiper.com>
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
 ******************************************************************************/

package com.jfastnet;

import com.jfastnet.messages.Message;
import com.jfastnet.processors.MessageLogProcessor;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;

/** @author Klaus Pfeiffer - klaus@allpiper.com */
public class MessageLogTest {

	private volatile int i;
	private volatile int j;

	static class NumberMessage extends Message {
		int number;
		public NumberMessage(int number) {
			this.number = number;
		}
	}

	@Test
	public void testMessageLog() throws InterruptedException {
		Config config = new Config();
		State state = new State(config);
		MessageLogProcessor.ProcessorConfig processorConfig = new MessageLogProcessor.ProcessorConfig();
		MessageLog messageLog = new MessageLog(config, processorConfig);
		processorConfig.messageLogReceiveFilter = new MessageLog.ReliableMessagesPredicate();
		assertThat(messageLog.getReceived().size(), is(0));
		messageLog.addReceived(new Message() {
			@Override
			public ReliableMode getReliableMode() {
				return ReliableMode.UNRELIABLE;
			}
		});
		assertThat(messageLog.getReceived().size(), is(0));
		messageLog.addReceived(new Message() {
			@Override
			public ReliableMode getReliableMode() {
				return ReliableMode.SEQUENCE_NUMBER;
			}
		});
		assertThat(messageLog.getReceived().size(), is(1));

		Thread t1 = new Thread(() -> {
			while (true) {
				if (i < j + config.getAdditionalConfig(MessageLogProcessor.ProcessorConfig.class).getSentMessagesMapLimit() - 1500) {
					i++;
					NumberMessage msg = new NumberMessage(i);
					msg.resolve(config, state);
					msg.resolveId();
					messageLog.addSent(msg);
				}
			}
		});
		t1.start();

		int count = 4000;
		// Wait for count number to be generated
		while (i < count);
		j = 3000;
		while (j < count) {
			j++;
			while (i < j + 1000);

			Message msg;

			msg = messageLog.getSent(MessageKey.newKey(Message.ReliableMode.SEQUENCE_NUMBER, 0, j));
			assertNotNull("msg id: " + j + ", i=" + i, msg);
			assertThat(msg.getMsgId(), is((long) j));

			int offset = 500;
			msg = messageLog.getSent(MessageKey.newKey(Message.ReliableMode.SEQUENCE_NUMBER, 0, j - offset));
			assertThat(msg, is(notNullValue()));
			assertThat(msg.getMsgId(), is((long) j - offset));
		}
		t1.interrupt();
	}
}