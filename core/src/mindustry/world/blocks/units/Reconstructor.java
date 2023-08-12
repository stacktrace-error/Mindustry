package mindustry.world.blocks.units;

import arc.*;
import arc.Graphics.*;
import arc.Graphics.Cursor.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.Block;
import mindustry.world.blocks.payloads.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class Reconstructor extends UnitBlock{
    public float constructTime = 60 * 2;
    public Seq<UnlockableContent[]> upgrades = new Seq<>();
    public int[] capacities = {};

    public Reconstructor(String name){
        super(name);
        regionRotated1 = 1;
        regionRotated2 = 2;
        commandable = true;
        ambientSound = Sounds.respawning;
        configurable = true;
        config(UnitCommand.class, (ReconstructorBuild build, UnitCommand command) -> build.command = command);
        configClear((ReconstructorBuild build) -> build.command = null);
        group = BlockGroup.units;
        outputsPayload = true;
        rotate = true;
        update = true;
        solid = true;
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
        Draw.rect(region, plan.drawx(), plan.drawy());
        Draw.rect(inRegion, plan.drawx(), plan.drawy(), plan.rotation * 90);
        Draw.rect(outRegion, plan.drawx(), plan.drawy(), plan.rotation * 90);
        Draw.rect(topRegion, plan.drawx(), plan.drawy());
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region, inRegion, outRegion, topRegion};
    }

    @Override
    public void setBars(){
        super.setBars();

        addBar("progress", (ReconstructorBuild entity) -> new Bar("bar.progress", Pal.ammo, entity::fraction));
        addBar("units", (ReconstructorBuild e) ->
        new Bar(
            () -> e.unit() == null ? "[lightgray]" + Iconc.cancel :
                Core.bundle.format("bar.unitcap",
                    Fonts.getUnicodeStr(e.unit().name),
                    e.team.data().countType(e.unit()),
                    Units.getStringCap(e.team)
                ),
            () -> Pal.power,
            () -> e.unit() == null ? 0f : (float)e.team.data().countType(e.unit()) / Units.getCap(e.team)
        ));
    }

    @Override
    public void setStats(){
        stats.timePeriod = constructTime;
        super.setStats();

        stats.add(Stat.productionTime, constructTime / 60f, StatUnit.seconds);
        stats.add(Stat.output, table -> {
            table.row();
            for(var upgrade : upgrades){
                if(upgrade[0].unlockedNow() && upgrade[1].unlockedNow()){
                    table.table(Styles.grayPanel, t -> {
                        t.left();

                        t.image(upgrade[0].uiIcon).size(40).pad(10f).left().scaling(Scaling.fit);
                        t.table(info -> {
                            info.add(upgrade[0].localizedName).left();
                            info.row();
                        }).pad(10).left();
                    }).fill().padTop(5).padBottom(5);

                    table.table(Styles.grayPanel, t -> {

                        t.image(Icon.right).color(Pal.darkishGray).size(40).pad(10f);
                    }).fill().padTop(5).padBottom(5);

                    table.table(Styles.grayPanel, t -> {
                        t.left();

                        t.image(upgrade[1].uiIcon).size(40).pad(10f).right().scaling(Scaling.fit);
                        t.table(info -> {
                            info.add(upgrade[1].localizedName).right();
                            info.row();
                        }).pad(10).right();
                    }).fill().padTop(5).padBottom(5);

                    table.row();
                }
            }
        });
    }

    @Override
    public void init(){
        capacities = new int[Vars.content.items().size];

        ConsumeItems cons = findConsumer(c -> c instanceof ConsumeItems);
        if(cons != null){
            for(ItemStack stack : cons.items){
                capacities[stack.item.id] = Math.max(capacities[stack.item.id], stack.amount * 2);
                itemCapacity = Math.max(itemCapacity, stack.amount * 2);
            }
        }

        consumeBuilder.each(c -> c.multiplier = b -> state.rules.unitCost(b.team));

        super.init();
    }

    public void addUpgrade(UnlockableContent from, UnlockableContent to){
        upgrades.add(new UnlockableContent[]{from, to});
    }

    public class ReconstructorBuild extends PayloadBlockBuild<Payload>{
        public float progress, time, speedScl;
        public @Nullable Vec2 commandPos;
        public @Nullable UnitCommand command;

        public float fraction(){
            return progress / constructTime;
        }

        @Override
        public boolean shouldActiveSound(){
            return shouldConsume();
        }

        @Override
        public Vec2 getCommandPosition(){
            return commandPos;
        }

        @Override
        public void onCommand(Vec2 target){
            commandPos = target;
        }

        @Override
        public boolean acceptUnitPayload(Unit unit){
            return hasUpgrade(unit.type) && !banned(upgrade(unit.type));
        }

        public boolean canSetCommand(){
            var output = unit();
            return output != null && output.commands.length > 1;
        }

        @Override
        public Cursor getCursor(){
            return canSetCommand() ? super.getCursor() : SystemCursor.arrow;
        }

        @Override
        public boolean shouldShowConfigure(Player player){
            return canSetCommand();
        }

        @Override
        public void buildConfiguration(Table table){
            var unit = unit();

            if(unit == null){
                deselect();
                return;
            }

            var group = new ButtonGroup<ImageButton>();
            group.setMinCheckCount(0);
            int i = 0, columns = 4;

            table.background(Styles.black6);

            var list = unit().commands;
            for(var item : list){
                ImageButton button = table.button(item.getIcon(), Styles.clearNoneTogglei, 40f, () -> {
                    configure(item);
                    deselect();
                }).tooltip(item.localized()).group(group).get();

                button.update(() -> button.setChecked(command == item || (command == null && unit.defaultCommand == item)));

                if(++i % columns == 0){
                    table.row();
                }
            }
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload){
            if(!(this.payload == null
            && (this.enabled || source == this)
            && relativeTo(source) != rotation)){
                return false;
            }

            var upgrade = upgrade(payload.content());

            if(upgrade != null){
                if(!upgrade.unlockedNowHost() && !team.isAI()){
                    //flash "not researched"
                    payload.showOverlay(Icon.tree);
                }

                if(banned(upgrade)){
                    //flash an X, meaning 'banned'
                    payload.showOverlay(Icon.cancel);
                }
            }

            return upgrade != null && (team.isAI() || upgrade.unlockedNowHost()) && !banned(upgrade);
        }

        @Override
        public int getMaximumAccepted(Item item){
            return Mathf.round(capacities[item.id] * state.rules.unitCost(team));
        }

        @Override
        public void overwrote(Seq<Building> builds){
            if(builds.first().block == block){
                items.add(builds.first().items);
            }
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);

            //draw input
            boolean fallback = true;
            for(int i = 0; i < 4; i++){
                if(blends(i) && i != rotation){
                    Draw.rect(inRegion, x, y, (i * 90) - 180);
                    fallback = false;
                }
            }
            if(fallback) Draw.rect(inRegion, x, y, rotation * 90);

            Draw.rect(outRegion, x, y, rotdeg());

            if(constructing() && hasArrived()){
                Draw.draw(Layer.blockOver, () -> {
                    Draw.alpha(1f - progress/ constructTime);
                    Draw.rect(payload.content().fullIcon, x, y, payload instanceof UnitPayload ? payload.rotation() - 90 : 0f);
                    Draw.reset();
                    Drawf.construct(this, upgrade(payload.content()), payload instanceof UnitPayload ? payload.rotation() - 90 : 0f, progress / constructTime, speedScl, time);
                });
            }else{
                Draw.z(Layer.blockOver);

                drawPayload();
            }

            Draw.z(Layer.blockOver + 0.1f);
            Draw.rect(topRegion, x, y);
        }

        @Override
        public Object senseObject(LAccess sensor){
            if(sensor == LAccess.config) return unit();
            return super.senseObject(sensor);
        }

        @Override
        public void updateTile(){ //todo needs unit()
            boolean valid = false;

            if(payload != null){
                //check if offloading
                if(!hasUpgrade(payload.content())){
                    moveOutPayload();
                }else{ //update progress
                    if(moveInPayload()){
                        if(efficiency > 0){
                            valid = true;
                            progress += edelta() * state.rules.unitBuildSpeed(team);
                        }

                        //upgrade the unit
                        if(progress >= constructTime){
                            payload = createPayload(upgrade(payload.content()));


                            if(payload instanceof UnitPayload pay && pay.unit.isCommandable()){
                                if(commandPos != null){
                                    pay.unit.command().commandPosition(commandPos);
                                }
                                if(command != null){
                                    //this already checks if it is a valid command for the unit type
                                    pay.unit.command().command(command);
                                }
                                Events.fire(new UnitCreateEvent(pay.unit, this));
                            }

                            progress %= 1f;
                            Effect.shake(2f, 3f, this);
                            Fx.producesmoke.at(this);
                            consume();
                        }
                    }
                }
            }

            speedScl = Mathf.lerpDelta(speedScl, Mathf.num(valid), 0.05f);
            time += edelta() * speedScl * state.rules.unitBuildSpeed(team);
        }

        @Override
        public void dumpPayload(){
            //translate payload forward slightly
            float tx = Angles.trnsx(payload.rotation(), 0.1f), ty = Angles.trnsy(payload.rotation(), 0.1f);
            payload.set(payload.x() + tx, payload.y() + ty, payload.rotation());

            if(payload.dump() && payload instanceof UnitPayload){
                payload = null;
                Call.unitBlockSpawn(tile);
            }else{
                payload.set(payload.x() - tx, payload.y() - ty, payload.rotation());
            }
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.progress) return Mathf.clamp(fraction());
            return super.sense(sensor);
        }

        @Override
        public boolean shouldConsume(){
            return constructing() && enabled;
        }

        @Override
        public Object config(){
            return command;
        }

        public UnitType unit(){
            return payload == null || !(upgrade(payload.content()) instanceof UnitType) ? null : (UnitType)upgrade(payload.content());
        }

        public boolean banned(UnlockableContent content){
            return content instanceof UnitType u ? u.isBanned() : state.rules.isBanned((Block)content);
        }

        public UnlockableContent content(){
            if(payload == null) return null;

            UnlockableContent t = upgrade(payload.content());
            return t != null && (t.unlockedNowHost() || team.isAI()) ? t : null;
        }

        public boolean constructing(){
            return payload != null && hasUpgrade(payload.content());
        }

        public boolean hasUpgrade(UnlockableContent type){
            UnlockableContent t = upgrade(type);
            return t != null && (t.unlockedNowHost() || team.isAI()) && !banned(type);
        }

        public UnlockableContent upgrade(UnlockableContent type){
            UnlockableContent[] r =  upgrades.find(u -> u[0] == type);
            return r == null ? null : r[1];
        }

        @Override
        public byte version(){
            return 3;
        }

        @Override
        public void write(Writes write){
            super.write(write);

            write.f(progress);
            TypeIO.writeVecNullable(write, commandPos);
            TypeIO.writeCommand(write, command);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            if(revision >= 1){
                progress = read.f();
            }

            if(revision >= 2){
                commandPos = TypeIO.readVecNullable(read);
            }

            if(revision >= 3){
                command = TypeIO.readCommand(read);
            }
        }

    }
}
