# Flyway migrations

`V1__create_approved_schema.sql` is the baseline migration for the approved 38-table ERD. V2 applies the approved `residents.email` width correction. Future reviewed SQL migrations use Flyway's versioned naming convention, for example `V3__<capability>_<change>.sql`. Assign the next version after checking the migrations already merged to `main`; coordinate version numbers across concurrent feature branches. Once applied to a database, a migration is immutable—make later changes in a new version.

Do not add empty migrations or edit an applied migration. Schema updates must preserve the approved ERD and its feature ownership; migration history on the database is authoritative for applied versions.
