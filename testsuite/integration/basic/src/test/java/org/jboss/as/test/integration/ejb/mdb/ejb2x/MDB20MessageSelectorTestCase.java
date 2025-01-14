/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.ejb.mdb.ejb2x;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.test.integration.common.jms.JMSOperations;
import org.jboss.as.test.integration.common.jms.JMSOperationsProvider;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import jakarta.jms.Message;
import jakarta.jms.Queue;
import javax.naming.InitialContext;
import java.util.PropertyPermission;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;

import jakarta.jms.QueueRequestor;
import jakarta.jms.QueueSession;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.junit.After;

/**
 * Tests EJB2.0 MDBs with message selector.
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
@RunWith(Arquillian.class)
@ServerSetup({MDB20MessageSelectorTestCase.JmsQueueSetup.class})
public class MDB20MessageSelectorTestCase extends AbstractMDB2xTestCase {

    private Queue queue;
    private Queue replyQueueA;
    private Queue replyQueueB;

    static class JmsQueueSetup implements ServerSetupTask {

        private JMSOperations jmsAdminOperations;

        @Override
        public void setup(ManagementClient managementClient, String containerId) throws Exception {
            jmsAdminOperations = JMSOperationsProvider.getInstance(managementClient.getControllerClient());
            jmsAdminOperations.createJmsQueue("ejb2x/queue", "java:jboss/ejb2x/queue");
            jmsAdminOperations.createJmsQueue("ejb2x/replyQueueA", "java:jboss/ejb2x/replyQueueA");
            jmsAdminOperations.createJmsQueue("ejb2x/replyQueueB", "java:jboss/ejb2x/replyQueueB");
        }

        @Override
        public void tearDown(ManagementClient managementClient, String containerId) throws Exception {
            if (jmsAdminOperations != null) {
                jmsAdminOperations.removeJmsQueue("ejb2x/queue");
                jmsAdminOperations.removeJmsQueue("ejb2x/replyQueueA");
                jmsAdminOperations.removeJmsQueue("ejb2x/replyQueueB");
                jmsAdminOperations.close();
            }
        }
    }

    @Deployment
    public static Archive getDeployment() {
        final JavaArchive ejbJar = ShrinkWrap.create(JavaArchive.class, "mdb.jar");
        ejbJar.addClasses(EJB2xMDB.class, AbstractMDB2xTestCase.class);
        ejbJar.addPackage(JMSOperations.class.getPackage());
        ejbJar.addClasses(JmsQueueSetup.class, TimeoutUtil.class);
        ejbJar.addAsManifestResource(MDB20MessageSelectorTestCase.class.getPackage(), "ejb-jar-20-message-selector.xml", "ejb-jar.xml");
        ejbJar.addAsManifestResource(MDB20MessageSelectorTestCase.class.getPackage(), "jboss-ejb3.xml", "jboss-ejb3.xml");
        ejbJar.addAsManifestResource(new StringAsset("Dependencies: org.jboss.as.controller-client, org.jboss.dmr,  org.apache.activemq.artemis\n"), "MANIFEST.MF");
        ejbJar.addAsManifestResource(createPermissionsXmlAsset(new PropertyPermission("ts.timeout.factor", "read")), "jboss-permissions.xml");
        return ejbJar;
    }

    @Before
    public void initQueues() {
        try {
            final InitialContext ic = new InitialContext();
            queue = (Queue) ic.lookup("java:jboss/ejb2x/queue");
            replyQueueA = (Queue) ic.lookup("java:jboss/ejb2x/replyQueueA");
            replyQueueB = (Queue) ic.lookup("java:jboss/ejb2x/replyQueueB");
            purgeQueue("ejb2x/queue");
            purgeQueue("ejb2x/replyQueueA");
            purgeQueue("ejb2x/replyQueueB");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void purgeQueues() throws Exception {
        purgeQueue("ejb2x/queue");
        purgeQueue("ejb2x/replyQueueA");
        purgeQueue("ejb2x/replyQueueB");
    }

    /**
     * Tests 2 messages, only one of them with the selected format.
     */
    @Test
    public void testMessageSelectors() {
        sendTextMessage("Say 1st hello to " + EJB2xMDB.class.getName() + " in 1.0 format", queue, replyQueueA, "Version 1.0");
        final Message replyA = receiveMessage(replyQueueA, TimeoutUtil.adjust(5000));
        Assert.assertNull("Unexpected reply from " + replyQueueA, replyA);

        sendTextMessage("Say 2nd hello to " + EJB2xMDB.class.getName() + " in 1.1 format", queue, replyQueueB, "Version 1.1");
        final Message replyB = receiveMessage(replyQueueB, TimeoutUtil.adjust(5000));
        Assert.assertNotNull("Missing reply from " + replyQueueB, replyB);
    }

    /**
     * Re-tests 2 messages, both of them with the selected format.
     */
    @Test
    public void retestMessageSelectors() {
        sendTextMessage("Say 1st hello to " + EJB2xMDB.class.getName() + " in 1.1 format", queue, replyQueueA, "Version 1.1");
        final Message replyA = receiveMessage(replyQueueA, TimeoutUtil.adjust(5000));
        Assert.assertNotNull("Missing reply from " + replyQueueA, replyA);

        sendTextMessage("Say 2nd hello to " + EJB2xMDB.class.getName() + " in 1.1 format", queue, replyQueueB, "Version 1.1");
        final Message replyB = receiveMessage(replyQueueB, TimeoutUtil.adjust(5000));
        Assert.assertNotNull("Missing reply from " + replyQueueB, replyB);
    }

    /**
     * Removes all message son a queue
     *
     * @param queueName name of the queue
     * @throws Exception
     */
    private void purgeQueue(String queueName) throws Exception {
        QueueRequestor requestor = new QueueRequestor((QueueSession) session, ActiveMQJMSClient.createQueue("activemq.management"));
        Message m = session.createMessage();
        org.apache.activemq.artemis.api.jms.management.JMSManagementHelper.putOperationInvocation(m, ResourceNames.QUEUE + "jms.queue." + queueName, "removeAllMessages");
        Message reply = requestor.request(m);
        if (!reply.getBooleanProperty("_AMQ_OperationSucceeded")) {
            logger.warn(reply.getBody(String.class));
        }
    }
}
