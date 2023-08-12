package mindustry.world.blocks.payloads;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.style.TextureRegionDrawable;
import arc.util.*;
import arc.util.io.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class BuildPayload implements Payload{
    public Building build;
    public float overlayTime = 0f;
    public @Nullable TextureRegion overlayRegion;

    public BuildPayload(Block block, Team team){
        this.build = block.newBuilding().create(block, team);
        this.build.tile = emptyTile;
    }

    public BuildPayload(Building build){
        this.build = build;
    }

    /** Flashes a red overlay region. */
    public void showOverlay(TextureRegion icon){
        overlayRegion = icon;
        overlayTime = 1f;
    }

    /** Flashes a red overlay region. */
    public void showOverlay(TextureRegionDrawable icon){
        if(icon == null || headless) return;
        showOverlay(icon.getRegion());
    }

    public Block block(){
        return build.block;
    }

    public void place(Tile tile){
        place(tile, 0);
    }

    public void place(Tile tile, int rotation){
        tile.setBlock(build.block, build.team, rotation, () -> build);
        build.dropped();
    }

    @Override
    public UnlockableContent content(){
        return build.block;
    }

    @Override
    public void update(@Nullable Unit unitHolder, @Nullable Building buildingHolder){
        if(unitHolder != null && (!build.block.updateInUnits || (!state.rules.unitPayloadUpdate && !build.block.alwaysUpdateInUnits))) return;

        build.tile = emptyTile;
        build.updatePayload(unitHolder, buildingHolder);
    }

    @Override
    public ItemStack[] requirements(){
        return build.block.requirements;
    }

    @Override
    public float buildTime(){
        return build.block.buildCost;
    }

    @Override
    public float x(){
        return build.x;
    }

    @Override
    public float y(){
        return build.y;
    }

    @Override
    public float size(){
        return build.block.size * tilesize;
    }

    @Override
    public void write(Writes write){
        write.b(payloadBlock);
        write.s(build.block.id);
        write.b(build.version());
        build.writeAll(write);
    }

    @Override
    public void set(float x, float y, float rotation){
        build.set(x, y);
        build.payloadRotation = rotation;
    }

    @Override
    public void drawShadow(float alpha){
        Drawf.squareShadow(build.x, build.y, build.block.size * tilesize * 1.85f, alpha);
    }

    @Override
    public void draw(){
        float prevZ = Draw.z();
        Draw.z(prevZ - 0.0001f);
        drawShadow(1f);
        Draw.z(prevZ);
        Draw.zTransform(z -> z >= Layer.flyingUnitLow + 1f ? z : 0.0011f + Math.min(Mathf.clamp(z, prevZ - 0.001f, prevZ + 0.9f), Layer.flyingUnitLow - 1f));
        build.tile = emptyTile;
        build.payloadDraw();
        Draw.zTransform();
        Draw.z(prevZ);

        //draw warning
        if(overlayTime > 0){
            var region = overlayRegion == null ? Icon.warning.getRegion() : overlayRegion;
            Draw.color(Color.scarlet);
            Draw.alpha(0.8f * Interp.exp5Out.apply(overlayTime));

            float size = 8f;
            Draw.rect(region, build.x, build.y, size, size);

            Draw.reset();

            overlayTime = Math.max(overlayTime - Time.delta/overlayDuration, 0f);
        }
    }

    @Override
    public TextureRegion icon(){
        return block().fullIcon;
    }
}
