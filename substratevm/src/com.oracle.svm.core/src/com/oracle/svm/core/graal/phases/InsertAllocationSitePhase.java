package com.oracle.svm.core.graal.phases;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedType;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.Phase;

import java.util.Arrays;

import static com.oracle.svm.core.jdk.IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION;

public class InsertAllocationSitePhase extends Phase {
    private final int methodMask = 0x3fff0000;
    private final int allocationCounterMask = 0x0000ffff;
    // Used as the second unique identifier;
    private int allocationCounter = 1;
    private int totalAllocations = 0;
    private String[] skippablePackage = {"java.", "com.", "jdk.", "sun.", "org."};

    @Override
    protected void run(StructuredGraph graph) {
        for(AbstractNewObjectNode n : graph.getNodes().filter(AbstractNewObjectNode.class)){
            if (Arrays.stream(skippablePackage).anyMatch(e -> n.graph().method().format("%H").startsWith(e))) {
                // Used as a value we skip together with the OLD table
                n.setPersonalAllocationSite(0);
                continue;
            } else if (n instanceof NewInstanceNode){
                SharedType type = (SharedType) ((NewInstanceNode) n).instanceClass();
                DynamicHub hub = type.getHub();
                setupNode(hub, graph, n);
            } else if (n instanceof NewArrayNode) {
                SharedType type = (SharedType) ((NewArrayNode) n).elementType();
                DynamicHub hub = type.getHub().getArrayHub();
                setupNode(hub, graph, n);
            }
        }
    }

    private void setupNode(DynamicHub hub, StructuredGraph graph, AbstractNewObjectNode n){
        int hubHashCode = hub.getHashCodeOffset();
        //hashCodeOffset constant value
        ConstantNode hashCodeOffsetNode = ConstantNode.forInt(hubHashCode);
        graph.addWithoutUnique(hashCodeOffsetNode);
        //the whole address for the hashCodeOffset
        AddressNode address = new OffsetAddressNode(n, hashCodeOffsetNode);
        graph.unique(address);
        int allocationSiteForNode = createAllocationSiteForNode(n);
        ConstantNode allocationSiteValueNode = ConstantNode.forInt(allocationSiteForNode);
        //the node we use to writing to memory (or overwriting the object hashcode)
        WriteNode writeNode = new WriteNode(address,
                IDENTITY_HASHCODE_LOCATION, allocationSiteValueNode, OnHeapMemoryAccess.BarrierType.UNKNOWN);
        graph.addWithoutUnique(allocationSiteValueNode);
        graph.addAfterFixed(n, writeNode);
        graph.add(writeNode);
        n.setPersonalAllocationSite(allocationSiteForNode);
        totalAllocations++;
    }

    private int createAllocationSiteForNode(ValueNode node) {
        int allocationSite = allocationCounter;
        incrementAllocationCounter();
        return allocationSite;
    }

    private void incrementAllocationCounter(){
        ++allocationCounter;
    }
}