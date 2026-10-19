Van Rental System — Getting Started Plan
How we'll split the work
Planning and design happen here in chat — entity design, schema decisions, the "why" behind each choice. Actual coding happens in Claude Code, a separate tool that runs in the terminal and edits files directly in the project folder. Decisions made here get carried over and implemented there.
First steps (before touching Claude Code)
1. Verify PostgreSQL install — open pgAdmin, or run psql --version in a terminal.
2. Set up the Git repo — create a parent folder (e.g. van-rental-system) with backend/ (Spring Boot) and frontend/ (Angular) subfolders, then git init.
3. Finalize the database schema — nail down the entities and relationships before any code is written, so Claude Code can move fast and accurately once we start building.
Once the schema is settled, that's the hand-off point: bring the finalized design into Claude Code to generate the actual Entity classes, controllers, and Angular components.
Open questions to finalize the schema
[ ] Who uses the system — public customers booking online themselves, or staff logging bookings internally (digital record-keeping, not self-service)?
[ ] Pricing model — fixed rate per van per day, or does it vary by purpose or distance?