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

package dynamicswordskills.network.server;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import dynamicswordskills.entity.DSSPlayerInfo;
import dynamicswordskills.skills.ICombo;
import dynamicswordskills.skills.SkillBase;

/**
 * 
 * This packet simply informs the server when an attack combo should be ended prematurely.
 * If a combo ends on the server side, the Combo class' own endCombo method should be used
 * directly instead of sending a packet.
 *
 */
public class EndComboPacket implements IMessage
{
	/** Id of skill that implements ICombo */
	private byte id;

	public EndComboPacket() {}

	public EndComboPacket(SkillBase skill) {
		this.id = skill.getId();
	}

	@Override
	public void fromBytes(ByteBuf buffer) {
		id = buffer.readByte();
	}

	@Override
	public void toBytes(ByteBuf buffer) {
		buffer.writeByte(id);
	}

	public static class Handler extends AbstractServerMessageHandler<EndComboPacket> {
		@Override
		public IMessage handleServerMessage(EntityPlayer player, EndComboPacket msg, MessageContext ctx) {
			if (SkillBase.getSkill(msg.id) instanceof ICombo) {
				ICombo skill = (ICombo) DSSPlayerInfo.get(player).getPlayerSkill(msg.id);
				if (skill != null) {
					if (skill.isComboInProgress()) {
						skill.getCombo().endCombo(player);
					} else {
						skill.setCombo(null);
					}
				}
			}
			return null;
		}
	}
}
