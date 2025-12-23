package medievalsim.grandexchange.support;

import necesse.engine.network.server.Server;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.world.WorldEntity;
import necesse.level.maps.Level;

/**
 * Minimal {@link Level} implementation that pretends to be a server-side level
 * without requiring a fully bootstrapped {@link Server} instance. This allows
 * us to exercise LevelData flows (banking + grand exchange) inside unit tests
 * while the rest of the Necesse runtime is absent.
 */
public class HeadlessServerLevel extends Level {

    public HeadlessServerLevel() {
        this(LevelIdentifier.SURFACE_IDENTIFIER, 64, 64, WorldEntity.getDebugWorldEntity());
    }

    public HeadlessServerLevel(LevelIdentifier identifier, int width, int height, WorldEntity worldEntity) {
        super(identifier, width, height, worldEntity);
    }

    @Override
    public boolean isServer() {
        return true;
    }

    @Override
    public Server getServer() {
        return null;
    }
}
