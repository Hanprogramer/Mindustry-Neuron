package io.anuke.mindustry;

import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.TextureRegion;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.Log;
import io.anuke.arc.util.noise.RidgedPerlin;
import io.anuke.mindustry.ImagePacker.GenRegion;
import io.anuke.mindustry.type.*;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Block.Icon;
import io.anuke.mindustry.world.blocks.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.anuke.mindustry.Vars.content;
import static io.anuke.mindustry.Vars.tilesize;

public class Generators{

    public static void generate(){

        ImagePacker.generate("cracks", () -> {
            RidgedPerlin r = new RidgedPerlin(1, 3);
            for(int size = 1; size <= Block.maxCrackSize; size++){
                int dim = size * 32;
                int steps = Block.crackRegions;
                for(int i = 0; i < steps; i++){
                    float fract = i / (float)steps;

                    Image image = new Image(dim, dim);
                    for(int x = 0; x < dim; x++){
                        for(int y = 0; y < dim; y++){
                            float dst = Mathf.dst((float)x/dim, (float)y/dim, 0.5f, 0.5f) * 2f;
                            if(dst < 1.2f && r.getValue(x, y, 1f / 40f) - dst*(1f-fract) > 0.16f){
                                image.draw(x, y, Color.WHITE);
                            }
                        }
                    }

                    Image output = new Image(image.width, image.height);
                    int rad = 3;

                    //median filter
                    for(int x = 0; x < output.width; x++){
                        for(int y = 0; y < output.height; y++){
                            int whites = 0, clears = 0;
                            for(int cx = -rad; cx < rad; cx++){
                                for(int cy = -rad; cy < rad; cy++){
                                    int wx = Mathf.clamp(cx + x, 0, output.width - 1), wy = Mathf.clamp(cy + y, 0, output.height - 1);
                                    Color color = image.getColor(wx, wy);
                                    if(color.a > 0.5f){
                                        whites ++;
                                    }else{
                                        clears ++;
                                    }
                                }
                            }
                            output.draw(x, y, whites >= clears ? Color.WHITE : Color.CLEAR);
                        }
                    }

                    output.save("cracks-" + size + "-" + i);
                }
            }
        });

        ImagePacker.generate("block-icons", () -> {
            Image colors = new Image(content.blocks().size, 1);
            Color outlineColor = Color.valueOf("404049");

            for(Block block : content.blocks()){
                TextureRegion[] regions = block.getGeneratedIcons();

                try{
                    if(block instanceof Floor){
                        block.load();
                        for(TextureRegion region : block.variantRegions()){
                            GenRegion gen = (GenRegion)region;
                            if(gen.path == null) continue;
                            Files.copy(gen.path, Paths.get("../editor/editor-" + gen.path.getFileName()));
                        }
                    }
                }catch(IOException e){
                    throw new RuntimeException(e);
                }

                if(regions.length == 0){
                    continue;
                }

                try{
                    Image last = null;
                    if(block.outlineIcon){
                        int radius = 4;
                        GenRegion region = (GenRegion)regions[regions.length - 1];
                        Image base = ImagePacker.get(region);
                        Image out = last = new Image(region.getWidth(), region.getHeight());
                        for(int x = 0; x < out.width; x++){
                            for(int y = 0; y < out.height; y++){

                                Color color = base.getColor(x, y);
                                out.draw(x, y, color);
                                if(color.a < 1f){
                                    boolean found = false;
                                    outer:
                                    for(int rx = -radius; rx <= radius; rx++){
                                        for(int ry = -radius; ry <= radius; ry++){
                                            if(Mathf.dst(rx, ry) <= radius && base.getColor(rx + x, ry + y).a > 0.01f){
                                                found = true;
                                                break outer;
                                            }
                                        }
                                    }
                                    if(found){
                                        out.draw(x, y, outlineColor);
                                    }
                                }
                            }
                        }

                        try{
                            Files.delete(region.path);
                        }catch(IOException e){
                            e.printStackTrace();
                        }

                        out.save(block.name);
                    }

                    Image image = ImagePacker.get(regions[0]);

                    int i = 0;
                    for(TextureRegion region : regions){
                        i++;
                        if(i != regions.length || last == null){
                            image.draw(region);
                        }else{
                            image.draw(last);
                        }
                    }

                    if(regions.length > 1){
                        image.save(block.name + "-icon-full");
                    }

                    image.save("../editor/" + block.name + "-icon-editor");

                    for(Icon icon : Icon.values()){
                        if(icon.size == 0 || (icon.size == image.width && icon.size == image.height)) continue;
                        Image scaled = new Image(icon.size, icon.size);
                        scaled.drawScaled(image);
                        scaled.save(block.name + "-icon-" + icon.name());
                    }

                    Color average = new Color();
                    for(int x = 0; x < image.width; x++){
                        for(int y = 0; y < image.height; y++){
                            Color color = image.getColor(x, y);
                            average.r += color.r;
                            average.g += color.g;
                            average.b += color.b;
                        }
                    }
                    average.mul(1f / (image.width * image.height));
                    if(block instanceof Floor){
                        average.mul(0.8f);
                    }else{
                        average.mul(1.1f);
                    }
                    average.a = 1f;
                    colors.draw(block.id, 0, average);
                }catch(IllegalArgumentException e){
                    Log.info("Skipping &ly'{0}'", block.name);
                }catch(NullPointerException e){
                    Log.err("Block &ly'{0}'&lr has an null region!");
                }
            }

            colors.save("../../../assets/sprites/block_colors");
        });

        ImagePacker.generate("item-icons", () -> {
            for(Item item : content.items()){
                Image base = ImagePacker.get("item-" + item.name);
                for(Item.Icon icon : Item.Icon.values()){
                    if(icon.size == base.width) continue;
                    Image image = new Image(icon.size, icon.size);
                    image.drawScaled(base);
                    image.save("item-" + item.name + "-" + icon.name(), false);
                }
            }
        });

        ImagePacker.generate("mech-icons", () -> {
            for(Mech mech : content.<Mech>getBy(ContentType.mech)){
                mech.load();
                mech.weapon.load();

                Image image = ImagePacker.get(mech.region);

                if(!mech.flying){
                    image.drawCenter(mech.baseRegion);
                    image.drawCenter(mech.legRegion);
                    image.drawCenter(mech.legRegion, true, false);
                    image.drawCenter(mech.region);
                }

                int off = image.width / 2 - mech.weapon.region.getWidth() / 2;

                image.draw(mech.weapon.region, -(int)mech.weaponOffsetX + off, (int)mech.weaponOffsetY + off, false, false);
                image.draw(mech.weapon.region, (int)mech.weaponOffsetX + off, (int)mech.weaponOffsetY + off, true, false);


                image.save("mech-icon-" + mech.name);
            }
        });

        ImagePacker.generate("unit-icons", () -> {
            content.<UnitType>getBy(ContentType.unit).each(type -> !type.isFlying, type -> {
                type.load();
                type.weapon.load();

                Image image = ImagePacker.get(type.region);

                image.draw(type.baseRegion);
                image.draw(type.legRegion);
                image.draw(type.legRegion, true, false);
                image.draw(type.region);

                for(boolean b : Mathf.booleans){
                    image.draw(type.weapon.region,
                    (int)(Mathf.sign(b) * type.weapon.width / Draw.scl + image.width / 2 - type.weapon.region.getWidth() / 2),
                    (int)(type.weaponOffsetY / Draw.scl + image.height / 2f - type.weapon.region.getHeight() / 2f),
                    b, false);
                }

                image.save("unit-icon-" + type.name);
            });
        });

        ImagePacker.generate("ore-icons", () -> {
            content.blocks().<OreBlock>each(b -> b instanceof OreBlock, ore -> {
                Item item = ore.itemDrop;

                for(int i = 0; i < 3; i++){
                    //get base image to draw on
                    Image image = new Image(32, 32);
                    Image shadow = ImagePacker.get(item.name + (i + 1));

                    int offset = image.width / tilesize;

                    for(int x = 0; x < image.width; x++){
                        for(int y = offset; y < image.height; y++){
                            Color color = shadow.getColor(x, y - offset);

                            //draw semi transparent background
                            if(color.a > 0.001f){
                                color.set(0, 0, 0, 0.3f);
                                image.draw(x, y, color);
                            }
                        }
                    }

                    image.draw(ImagePacker.get(item.name + (i + 1)));
                    image.save("../blocks/environment/ore-" + item.name + (i + 1));
                    image.save("../editor/editor-ore-" + item.name + (i + 1));

                    //save icons
                    image.save(ore.name + "-icon-full");
                    for(Icon icon : Icon.values()){
                        if(icon.size == 0) continue;
                        Image scaled = new Image(icon.size, icon.size);
                        scaled.drawScaled(image);
                        scaled.save(ore.name + "-icon-" + icon.name());
                    }
                }
            });
        });

        ImagePacker.generate("edges", () -> {
            content.blocks().<Floor>each(b -> b instanceof Floor && !(b instanceof OverlayFloor), floor -> {

                if(ImagePacker.has(floor.name + "-edge") || floor.blendGroup != floor){
                    return;
                }

                try{
                    Image image = ImagePacker.get(floor.generateIcons()[0]);
                    Image edge = ImagePacker.get("edge-stencil");
                    Image result = new Image(edge.width, edge.height);

                    for(int x = 0; x < edge.width; x++){
                        for(int y = 0; y < edge.height; y++){
                            result.draw(x, y, edge.getColor(x, y).mul(image.getColor(x % image.width, y % image.height)));
                        }
                    }

                    result.save("../blocks/environment/" + floor.name + "-edge");

                }catch(Exception ignored){}
            });
        });
    }

}
