# Feature Documentation

Features are organized by the plugin that provides them. Each plugin has its own folder with a README index and one document per feature.

- **[Core — LeetCore](core/)**: seven standalone features registered by the shared-infrastructure plugin.
- **[Crafting — LeetCrafting](crafting/)**: the custom food/condiment items and recipes.
- **[Skills — LeetSkills](skills/)**: the XP-spent skill tree.
- **[Vanity — LeetVanity](vanity/)**: the hub feature of several distinct capabilities.

Every gated feature shares the same `base:`/`messages:` config layout, control model, and `leet.feat.<id>` permission lifecycle. That common ground is documented once in [ARCHITECTURE.md](../ARCHITECTURE.md#common-feature-config-layout) and [feature permissions](../ARCHITECTURE.md#feature-permissions); feature docs only link to it.
