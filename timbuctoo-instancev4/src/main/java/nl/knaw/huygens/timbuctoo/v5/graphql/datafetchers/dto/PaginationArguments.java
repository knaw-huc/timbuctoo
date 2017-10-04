package nl.knaw.huygens.timbuctoo.v5.graphql.datafetchers.dto;

import com.google.common.base.Charsets;
import org.immutables.value.Value;

import java.util.Base64;
import java.util.Optional;

@Value.Immutable
public interface PaginationArguments {

  Base64.Decoder DECODER = Base64.getDecoder();

  /**
   * Cursor is either
   *  - the empty string which indicates pagination from the first item forwards
   *  - the literal value "LAST" which indicates pagination from the last item backwards
   *  - a magic cookie (a token generated by the data provider and passed back verbatim) that should contain information
   *    on where to start and what direction to take.
   */
  String getCursor();


  /**
   * SearchQuery is a String-serialized elasticsearch query
   */
  Optional<String> getSearchQuery();

  /**
   * Count is either a positive number which provides a suggestion on how many items to return (might be overridden by
   * the data provider) or a negative number (usually -1) that indicates that there is no preference
   */
  int getCount();

  static PaginationArguments create(int count, String cursor, String searchQuery) {
    return ImmutablePaginationArguments.builder()
      .count(count)
      .searchQuery(Optional.ofNullable(searchQuery))
      .cursor(new String(DECODER.decode(cursor), Charsets.UTF_8))
      .build();
  }
}
