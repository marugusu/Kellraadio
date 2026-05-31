# Kellraadio: Agent Instructions & Rules Index

Welcome! This repository is optimized for **agentic development**. As an AI agent (such as Antigravity, Gemini, or Claude Code), you **MUST** read and adhere to the project rules and custom skills located in the `.agents/` folder before writing any code or proposing schema changes.

## Directory Structure

All rules and specialized capabilities are organized as follows:

```
.agents/
├── rules/
│   ├── general.md       # Architecture, styling, MVVM & Compose standards
│   └── database.md      # Room DB modifications and thread-safety
└── skills/
    ├── room-migration-helper/
    │   └── SKILL.md     # Guidelines for altering schemas and version increments
    ├── compose-component-generator/
    │   └── SKILL.md     # Guidelines for styling responsive UI screens/dialogues
    └── retrofit-api-integrator/
        └── SKILL.md     # Guidelines for wiring Retrofit network APIs
```

## How to use Rules & Skills

1.  **Read general rules**: At the start of your workspace analysis, view [general.md](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/rules/general.md) to understand the project architecture (MVVM, Jetpack Compose, Media3 ExoPlayer).
2.  **Load custom skills**: Before executing task-specific operations:
    *   If you are asked to modify database structures, read the [room-migration-helper](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/skills/room-migration-helper/SKILL.md) skill.
    *   If you are creating new UI components or tweaking layouts, read the [compose-component-generator](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/skills/compose-component-generator/SKILL.md) skill.
    *   If you are integrating new backend APIs or web endpoints, read the [retrofit-api-integrator](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/.agents/skills/retrofit-api-integrator/SKILL.md) skill.

By following these procedures, you ensure your implementations remain clean, consistent, and do not cause application failures or data corruption.
