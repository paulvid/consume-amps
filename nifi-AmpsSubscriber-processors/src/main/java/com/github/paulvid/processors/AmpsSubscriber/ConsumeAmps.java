/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.paulvid.processors.AmpsSubscriber;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.crankuptheamps.client.*;
import com.crankuptheamps.client.exception.*;


@Tags({"amps, consume, pubsub"})
@CapabilityDescription("Provide a description")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class ConsumeAmps extends AbstractProcessor {

    public static final PropertyDescriptor URL_PROPERTY = new PropertyDescriptor
            .Builder().name("Connection String")
            .displayName("Connection String")
            .description("Connection String of the Amps Instance. Example: tcp://127.0.0.1:9007/amps/json")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();


    public static final PropertyDescriptor TOPIC_PROPERTY = new PropertyDescriptor
            .Builder().name("Topic")
            .displayName("Topic")
            .description("Name of the topic to which the processor will subscribe.")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor CLIENT_PROPERTY = new PropertyDescriptor
            .Builder().name("Client")
            .displayName("Client")
            .description("Name of the client used to connect to your AMPS instance")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("A FlowFile is routed to this relationship after the article has been successfully extracted")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("A FlowFile is routed to this relationship if an error occurred during the article extraction")
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(URL_PROPERTY);
        descriptors.add(TOPIC_PROPERTY);
        descriptors.add(CLIENT_PROPERTY);
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {



    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.create();


        Client c = new Client(String.valueOf(context.getProperty(CLIENT_PROPERTY).evaluateAttributeExpressions(flowFile).getValue()));
        try {


                c.connect(String.valueOf(context.getProperty(URL_PROPERTY).evaluateAttributeExpressions(flowFile).getValue()));
                c.logon();

                // Subscribe

            MessageStream ms = c.subscribe(String.valueOf(context.getProperty(TOPIC_PROPERTY).evaluateAttributeExpressions(flowFile).getValue()));
            try {
                for (Message m : ms) {
                    flowFile = session.write(flowFile, out -> {
                        try (OutputStream outputStream = new BufferedOutputStream(out)) {
                            outputStream.write(m.getData().getBytes(StandardCharsets.UTF_8));
                        }
                    });

                    break;

                }
            } finally {ms.close();}

            session.transfer(flowFile, REL_SUCCESS);

        } catch (AMPSException e) { getLogger().error("Failed to consume topic due to {}", new Object[]{e});
            session.transfer(flowFile, REL_FAILURE);
            return;
        } finally
        {
            c.close();
        }












    }
}
