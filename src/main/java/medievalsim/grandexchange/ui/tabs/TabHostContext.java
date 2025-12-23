package medievalsim.grandexchange.ui.tabs;

import java.util.function.Consumer;
import java.util.function.Supplier;

import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormComponent;

/**
 * Lightweight bridge that lets tab view builders register components without
 * knowing about the ContainerForm implementation details. Every component
 * added through this context is automatically tracked so tab teardown works
 * consistently across tabs.
 */
public final class TabHostContext {
    private final Consumer<FormComponent> componentRegistrar;
    private final Supplier<Client> clientSupplier;
    private final Form form;

    public TabHostContext(Consumer<FormComponent> componentRegistrar,
                          Supplier<Client> clientSupplier,
                          Form form) {
        this.componentRegistrar = componentRegistrar;
        this.clientSupplier = clientSupplier;
        this.form = form;
    }

    public Client getClient() {
        return clientSupplier.get();
    }

    public Form getForm() {
        return form;
    }

    public <C extends FormComponent> C addComponent(C component) {
        componentRegistrar.accept(component);
        return component;
    }
}
