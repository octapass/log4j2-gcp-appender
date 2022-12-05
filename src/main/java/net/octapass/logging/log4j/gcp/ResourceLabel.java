package net.octapass.logging.log4j.gcp;

import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

@Plugin(name = "ResourceLabel", category = Node.CATEGORY, printObject = true)
public record ResourceLabel(String name, String value) {

    public ResourceLabel(@Required @PluginAttribute("name") String name, @Required @PluginAttribute("value") String value) {
        this.name = name;
        this.value = value;
    }

    @PluginFactory
    public static ResourceLabel createGCPResourceConfig(
            @Required @PluginAttribute("name") final String name,
            @Required @PluginAttribute("value") final String value) {
        return new ResourceLabel(name, value);
    }
}
