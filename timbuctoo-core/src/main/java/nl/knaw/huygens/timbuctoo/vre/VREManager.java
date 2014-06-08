package nl.knaw.huygens.timbuctoo.vre;

/*
 * #%L
 * Timbuctoo core
 * =======
 * Copyright (C) 2012 - 2014 Huygens ING
 * =======
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Singleton;

@Singleton
public class VREManager {

  private static final String DEFAULT_VRE = PrimitivesVRE.NAME;

  private final Map<String, VRE> vreMap;

  public VREManager() {
    vreMap = Maps.newHashMap();
    List<VRE> vreList = ImmutableList.<VRE> of( //
        new BaseVRE(), //
        new CKCCVRE(), //
        new DutchCaribbeanVRE(), //
        new WomenWritersVRE(), //
        new TestVRE());

    for (VRE vre : vreList) {
      vreMap.put(vre.getName(), vre);
    }
  }

  /**
   * Get's the VRE that belongs to {@code id}.
   * @param id the id of the VRE to get.
   * @return the VRE if one is found, null if the VRE cannot be found.
   */
  public VRE getVREById(String id) {
    return vreMap.get(id);
  }

  /**
   * Gets the VRE that is defined as the default.
   */
  public VRE getDefaultVRE() {
    return this.getVREById(DEFAULT_VRE);
  }

  public Set<String> getAvailableVREIds() {
    return vreMap.keySet();
  }

  public boolean doesVREExist(String vreId) {
    return vreMap.keySet().contains(vreId);
  }

  public Collection<VRE> getAllVREs() {
    return vreMap.values();
  }

}
