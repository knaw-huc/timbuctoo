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

import java.io.IOException;
import java.util.List;

public class CobwwwebRsVRE extends AbstractVRE {

  @Override
  protected Scope createScope() throws IOException {
    return new PackageScope("timbuctoo.model.cwrs");
  }

  @Override
  public String getScopeId() {
    return "cwrs";
  }

  @Override
  public String getName() {
    return "CobwwwebRs";
  }

  @Override
  public String getDescription() {
    return "VRE for the 'COBWWWEB-Serbia' subproject.";
  }

  @Override
  public String getDomainEntityPrefix() {
    return "cwrs";
  }

  @Override
  public List<String> getReceptionNames() {
    return WomenWritersVRE.RECEPTION_NAMES;
  }

}