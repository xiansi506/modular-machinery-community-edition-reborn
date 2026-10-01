package com.reborn.modularmachinery.machine;

/**
 * Whatever currently holds the smart-interface values a recipe may read — in this projection, the machine
 * controller's own block entity.
 *
 * <h2>Why this is an interface with one method</h2>
 *
 * <p>The original reached a value through a chain of objects: {@code MachineControllerBlockEntity ->
 * foundSmartInterfaces (Map&lt;SmartInterfaceProvider, String&gt;) -> SmartInterfaceProvider#getMachineData(pos)}.
 * The middle link exists only because the value lived on a <b>separate block entity</b> and the controller had to
 * keep track of which provider it had bound to which type. With the value stored on the controller, that whole
 * chain collapses to one call — which is the point of the merge, not a loss: the controller was already the only
 * access entry point ({@code RequirementInterfaceNumInput} received a {@code ProcessingComponent} whose provider
 * was reached through the controller).
 *
 * <p>It is a functional interface rather than a direct reference to
 * {@code MachineControllerBlockEntity} for the same reason {@code ParallelController} and {@code UpgradeBusUtility}
 * are narrow: the offline acceptance harness implements it over a plain map, so the requirement's arithmetic can
 * be driven without a world, and nothing in {@code recipe/} has to know what a block entity is.
 */
@FunctionalInterface
public interface SmartInterfaceValueSource {

    /** A source that holds nothing — the value of a structure with no controller behind it. */
    SmartInterfaceValueSource NONE = type -> null;

    /**
     * The value stored for one declared interface type, or {@code null} when this source holds none.
     *
     * <p>{@code null} is the original's own signal: {@code SmartInterfaceProvider#getMachineData(String)} returned
     * {@code null} and {@code RequirementInterfaceNumInput} turned that into
     * {@code component.missing.modularmachinery.interface.number}. It is deliberately <b>not</b> the same as
     * "zero": a machine that declares the type and was never edited holds the declared default, which may itself
     * be zero.
     */
    Float valueOf(String type);
}
