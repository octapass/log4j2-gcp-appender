package net.octapass.logging.log4j.gcp;

import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Plugin(name = "GoogleCloudResource", category = Node.CATEGORY, printObject = true)
public record GCPResourceConfig(@PluginAttribute("type") String resourceType,
                                @PluginElement("ResourceLabel") ResourceLabel[] labels) {

    public GCPResourceConfig(String resourceType, ResourceLabel[] labels) {
        this.resourceType = resourceType;
        this.labels = labels;
    }

    @PluginFactory
    public static GCPResourceConfig createGCPResourceConfig(
            @PluginAttribute("type") final String type,
            @PluginElement("ResourceLabel") final ResourceLabel[] labels) {
        return new GCPResourceConfig(type, labels);
    }

}
