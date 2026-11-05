# Project Guidelines

## General
- Follow existing project architecture.
- Do not introduce new dependencies without approval.
- Prefer existing solutions over introducing new abstractions.
- Do not modify public APIs without explicit approval.
- Keep changes focused on the requested task.

## Architecture
- Follow Clean Architecture principles.
- Keep business logic independent of UI.
- Use repository pattern for data access.
- Use dependency injection.
- Do not access databases directly from UI components.

## Kotlin
- Use Kotlin idioms.
- Prefer immutable data structures.
- Use StateFlow for observable UI state.
- Use SharedFlow for one-time events when appropriate.
- Avoid GlobalScope.
- Handle coroutine cancellation correctly.

## Compose
- Keep composables focused and reusable.
- Hoist state whenever appropriate.
- Do not put business logic inside composables.
- Follow unidirectional data flow.

## Testing
- Add tests for new business logic.
- Cover error and edge cases.
- Run relevant tests after changes.

## Workflow
1. Analyze the existing implementation.
2. Propose an implementation plan.
3. Identify affected modules.
4. Implement the changes.
5. Run tests and static analysis.
6. Summarize changes and potential risks.