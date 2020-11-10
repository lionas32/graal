/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.phases.common;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.word.LocationIdentity;


public class InsertAllocationSitePhase extends Phase {
    public static final LocationIdentity IDENTITY_HASHCODE_LOCATION = NamedLocationIdentity.mutable("identityHashCode");
    private final int methodMask = 0xffff0000;
    private final int allocationCounterMask = 0x0000ffff;
    //Used as the second unique identifier;
    private int allocationCounter = 0;
    private final int hashCodeOffset = 0x10;
    @Override
    protected void run(StructuredGraph graph) {
        for (NewInstanceNode n : graph.getNodes().filter(NewInstanceNode.class)) {
            if (n.instanceClass().toString().contains("SimpleObject")) {
                System.out.println("n.toString(Verbosity.All): " + n.toString(Verbosity.All));
                int allocationSiteForNode =  getAllocationSiteForNode(n);
                //hashCodeOffset constant value
                ConstantNode hashCodeOffsetNode = ConstantNode.forInt(hashCodeOffset);
                graph.addWithoutUnique(hashCodeOffsetNode);
                //the whole address for the hashCodeOffset
                AddressNode address = new OffsetAddressNode(n, hashCodeOffsetNode);
                graph.unique(address);
                //the node we use to writing to memory (or overwriting the object hashcode)
                ConstantNode allocationSiteValueNode =  ConstantNode.forInt(allocationSiteForNode);
                graph.addWithoutUnique(allocationSiteValueNode);
                WriteNode writeNode = new WriteNode(address,
                        IDENTITY_HASHCODE_LOCATION, allocationSiteValueNode, OnHeapMemoryAccess.BarrierType.UNKNOWN);
                graph.add(writeNode);
                graph.addAfterFixed(n, writeNode);
            }
        }
        allocationCounter++;
    }

    private int getAllocationSiteForNode(NewInstanceNode node){
        ResolvedJavaMethod method = node.graph().getMethods().get(0);
        int allocationSite = (method.format("%H.%n").hashCode() & methodMask) | (allocationCounter & allocationCounterMask);
        return allocationSite;
    }

}
