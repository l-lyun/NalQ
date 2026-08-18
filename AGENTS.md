# Repository Guide

This repository is a monorepo with three applications:

- `server/`: Java 21, Spring Boot 4, and Gradle
- `web/`: React, TypeScript, Vite, and pnpm
- `app/`: Expo, React Native, TypeScript, and pnpm

Before making changes, inspect the existing structure and conventions inside the application you are working on.

Keep changes scoped to the relevant application. Do not modify the other applications unless the task explicitly requires it.

After changes, run the relevant build, test, or type-check command for that application.
