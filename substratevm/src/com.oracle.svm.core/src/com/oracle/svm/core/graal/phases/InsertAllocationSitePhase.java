package com.oracle.svm.core.graal.phases;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.Phase;

import static com.oracle.svm.core.jdk.IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION;

public class InsertAllocationSitePhase extends Phase {
    private final int methodMask = 0x1fff0000;
    private final int allocationCounterMask = 0x0000ffff;
    // Used as the second unique identifier;
    private int allocationCounter = 0;
    @Override
    protected void run(StructuredGraph graph) {
        setupInstanceNodes(graph);
        setupArrayNodes(graph);
    }

    private void setupInstanceNodes(StructuredGraph graph){
        for (NewInstanceNode n : graph.getNodes().filter(NewInstanceNode.class)) {
            System.out.println("NEWINSTANCENODE");
            System.out.println("n.instanceClass().toString(): " + n.instanceClass().toString());
            System.out.println("n.toString(Verbosity.All): " + n.toString(Verbosity.All));
            SharedType type = (SharedType) n.instanceClass();
            DynamicHub hub = type.getHub();
            int hubHashCode = hub.getHashCodeOffset();
            System.out.println("n.getHashCodeOffset: " + hubHashCode);
            //hashCodeOffset constant value
            ConstantNode hashCodeOffsetNode = ConstantNode.forInt(hubHashCode);
            graph.addWithoutUnique(hashCodeOffsetNode);
            //the whole address for the hashCodeOffset
            AddressNode address = new OffsetAddressNode(n, hashCodeOffsetNode);
            graph.unique(address);
            int allocationSiteForNode = createAllocationSiteForNode(n);
            System.out.println("allocationSiteForNode: " + Integer.toHexString(allocationSiteForNode));
            ConstantNode allocationSiteValueNode = ConstantNode.forInt(allocationSiteForNode);
            //the node we use to writing to memory (or overwriting the object hashcode)
            WriteNode writeNode = new WriteNode(address,
                    IDENTITY_HASHCODE_LOCATION, allocationSiteValueNode, OnHeapMemoryAccess.BarrierType.UNKNOWN);
            graph.addWithoutUnique(allocationSiteValueNode);
            graph.addAfterFixed(n, writeNode);
            graph.add(writeNode);
            n.setPersonalAllocationSite(allocationSiteForNode);
        }
    }

    private void setupArrayNodes(StructuredGraph graph) {
        for (NewArrayNode n : graph.getNodes().filter(NewArrayNode.class)) {
            System.out.println("NEWARRAYNODE");
            System.out.println("n.elementType().toString(): " + n.elementType().toString());
            System.out.println("n.length().toString(): " + n.length().toString());
            System.out.println("n.toString(Verbosity.All): " + n.toString(Verbosity.All));
            SharedType type = (SharedType) n.elementType();
            DynamicHub hub = type.getHub().getArrayHub();
            int hubHashCode = hub.getHashCodeOffset();
            System.out.println("n.getHashCodeOffset: " + hubHashCode);
            //hashCodeOffset constant value
            ConstantNode hashCodeOffsetNode = ConstantNode.forInt(hubHashCode);
            graph.addWithoutUnique(hashCodeOffsetNode);
            //the whole address for the hashCodeOffset
            AddressNode address = new OffsetAddressNode(n, hashCodeOffsetNode);
            graph.unique(address);
            int allocationSiteForNode = createAllocationSiteForNode(n);
            System.out.println("allocationSiteForNode: " + Integer.toHexString(allocationSiteForNode));
            ConstantNode allocationSiteValueNode = ConstantNode.forInt(allocationSiteForNode);
            //the node we use to writing to memory (or overwriting the object hashcode)
            WriteNode writeNode = new WriteNode(address,
                    IDENTITY_HASHCODE_LOCATION, allocationSiteValueNode, OnHeapMemoryAccess.BarrierType.UNKNOWN);
            graph.addWithoutUnique(allocationSiteValueNode);
            graph.addAfterFixed(n, writeNode);
            graph.add(writeNode);
            n.setPersonalAllocationSite(allocationSiteForNode);
        }
    }

    private int createAllocationSiteForNode(ValueNode node) {
        ResolvedJavaMethod method = node.graph().getMethods().get(0);
        int allocationSite = (method.format("%H.%n").hashCode() & methodMask) | (allocationCounter & allocationCounterMask);
        incrementAllocationCounter();
        return allocationSite;
    }

    private void incrementAllocationCounter(){
        ++allocationCounter;
        if (allocationCounter > 0xffff) {
            allocationCounter = 0;
        }
    }
}