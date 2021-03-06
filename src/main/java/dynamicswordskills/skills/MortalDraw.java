/**
    Copyright (C) <2014> <coolAlias>

    This file is part of coolAlias' Zelda Sword Skills Minecraft Mod; as such,
    you can redistribute it and/or modify it under the terms of the GNU
    General Public License as published by the Free Software Foundation,
    either version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package dynamicswordskills.skills;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import dynamicswordskills.client.DSSKeyHandler;
import dynamicswordskills.entity.DSSPlayerInfo;
import dynamicswordskills.network.PacketDispatcher;
import dynamicswordskills.network.bidirectional.ActivateSkillPacket;
import dynamicswordskills.network.client.MortalDrawPacket;
import dynamicswordskills.ref.Config;
import dynamicswordskills.ref.ModInfo;
import dynamicswordskills.util.PlayerUtils;

/**
 * 
 * MORTAL DRAW
 * Activation: While empty-handed and locked on, hold the block key and attack
 * Effect: The art of drawing the sword, or Battoujutsu, is a risky but deadly move, capable
 * of inflicting deadly wounds on unsuspecting opponents with a lightning-fast blade strike
 * Exhaustion: 3.0F - (0.2F * level)
 * Damage: If successful, inflicts damage + (damage * multiplier)
 * Duration: Window of attack opportunity is (level + 2) ticks
 * Notes:
 * - The first sword found in the action bar will be used for the strike; plan accordingly
 * - There is a 1.5s cooldown between uses, representing re-sheathing of the sword
 *
 */
public class MortalDraw extends SkillActive
{
	/** Delay before skill can be used again */
	private static final int DELAY = 30;

	/** The time remaining during which the skill will succeed; also used as animation flag */
	private int attackTimer;

	/** Nearest sword slot index */
	private int swordSlot;

	/**
	 * The entity that attacked and was successfully drawn against the first time
	 * Used to continue canceling the attack event until mortal draw has finished executing
	 */
	private Entity target;

	public MortalDraw(String name) {
		super(name);
	}

	private MortalDraw(MortalDraw skill) {
		super(skill);
	}

	@Override
	public MortalDraw newInstance() {
		return new MortalDraw(this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(List<String> desc, EntityPlayer player) {
		desc.add(getDamageDisplay(100, true) + "%");
		desc.add(getTimeLimitDisplay(getAttackTime() - DELAY));
		desc.add(getExhaustionDisplay(getExhaustion()));
	}

	@Override
	public boolean isActive() {
		// subtract 2 to allow short window in which still considered active so that
		// the attacker defended against is not able to immediately damage the defender
		return attackTimer > DELAY - 2;
	}

	/**
	 * Animation flag should continue for a few ticks after the skill finishes to
	 * prevent immediate switching of weapons or attacking (it just 'feels' better)
	 */
	@Override
	@SideOnly(Side.CLIENT)
	public boolean isAnimating() {
		return attackTimer > (DELAY - 5);
	}

	@Override
	protected float getExhaustion() {
		return 3.0F - (0.2F * level);
	}

	/**
	 * The number of ticks for which this skill is considered 'active', plus the DELAY
	 * Set the attackTimer to this amount upon activation.
	 */
	private int getAttackTime() {
		return level + DELAY + 2;
	}

	/** Returns the amount by which damage will be increased, as a percent: [damage + (damage * x)] */
	private int getDamageMultiplier() {
		return 100 + (10 * level);
	}

	@Override
	public boolean canUse(EntityPlayer player) {
		swordSlot = -1;
		if (super.canUse(player) && player.getHeldItem() == null && attackTimer == 0) {
			for (int i = 0; i < 9; ++i) {
				ItemStack stack = player.inventory.getStackInSlot(i);
				if (stack != null && PlayerUtils.isSwordOrProvider(stack, this)) {
					swordSlot = i;
					break;
				}
			}
		}
		return swordSlot > -1;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean canExecute(EntityPlayer player) {
		return player.getHeldItem() == null && (Minecraft.getMinecraft().gameSettings.keyBindUseItem.isKeyDown()
				|| DSSKeyHandler.keys[DSSKeyHandler.KEY_BLOCK].isKeyDown());
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isKeyListener(Minecraft mc, KeyBinding key) {
		return (key == DSSKeyHandler.keys[DSSKeyHandler.KEY_ATTACK] || (Config.allowVanillaControls() && key == mc.gameSettings.keyBindAttack));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean keyPressed(Minecraft mc, KeyBinding key, EntityPlayer player) {
		if (canExecute(player)) {
			PacketDispatcher.sendToServer(new ActivateSkillPacket(this));
			return true;
		}
		return false;
	}

	@Override
	protected boolean onActivated(World world, EntityPlayer player) {
		attackTimer = getAttackTime();
		target = null;
		return isActive();
	}

	@Override
	protected void onDeactivated(World world, EntityPlayer player) {
		attackTimer = 0;
		swordSlot = -1;
		target = null;
	}

	@Override
	public void onUpdate(EntityPlayer player) {
		if (attackTimer > 0) {
			--attackTimer;
			if (attackTimer == DELAY && !player.worldObj.isRemote) {
				drawSword(player, null);
				if (player.getHeldItem() != null) {
					PacketDispatcher.sendTo(new MortalDrawPacket(), (EntityPlayerMP) player);
				}
			}
		}
	}

	@Override
	public boolean onBeingAttacked(EntityPlayer player, DamageSource source) {
		if (!player.worldObj.isRemote && source.getEntity() != null) {
			// Changed isActive to return true for an extra 2 ticks to allow canceling damage
			if (target == source.getEntity()) {
				return true;
			} else if (attackTimer > DELAY) {
				if (drawSword(player, source.getEntity())) {
					PacketDispatcher.sendTo(new MortalDrawPacket(), (EntityPlayerMP) player);
					target = source.getEntity();
					return true;
				} else { // failed - do not continue trying
					attackTimer = DELAY;
					target = null;
				}
			}
		}
		return false;
	}

	/**
	 * Call upon landing a mortal draw blow
	 */
	public void onImpact(EntityPlayer player, LivingHurtEvent event) {
		// need to check time again, due to 2-tick delay for damage prevention
		if (attackTimer > DELAY) {
			attackTimer = DELAY;
			event.ammount *= (1.0F + ((float) getDamageMultiplier() / 100F));
			PlayerUtils.playSoundAtEntity(player.worldObj, player, ModInfo.SOUND_MORTALDRAW, 0.4F, 0.5F);
		} else { // too late - didn't defend against this target!
			target = null;
		}
	}

	/**
	 * Returns true if the player was able to draw a sword; always triggered first on
	 * the server, whether when attacked or the timer runs out, and second on the
	 * client after MortalDrawPacket is received.
	 * @return	true if the sword was drawn; always false on the client, as the player
	 * 			already drew the sword on the server
	 */
	public boolean drawSword(EntityPlayer player, Entity attacker) {
		boolean flag = false;
		if (!player.worldObj.isRemote) { // client player's held item synced automatically
			if (swordSlot > -1 && swordSlot != player.inventory.currentItem && player.getHeldItem() == null) {
				player.setCurrentItemOrArmor(0, player.inventory.getStackInSlot(swordSlot));
				player.inventory.setInventorySlotContents(swordSlot, null);
				ILockOnTarget skill = DSSPlayerInfo.get(player).getTargetingSkill();
				flag = (skill != null && skill.getCurrentTarget() == attacker);
			}
		}
		swordSlot = -1;
		return flag;
	}
}
