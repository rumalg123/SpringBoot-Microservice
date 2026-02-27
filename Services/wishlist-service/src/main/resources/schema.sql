CREATE UNIQUE INDEX IF NOT EXISTS uk_collection_keycloak_default
    ON wishlist_collections (keycloak_id) WHERE is_default = true;
