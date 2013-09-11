package nl.knaw.huygens.repository.persistence;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import nl.knaw.huygens.persistence.PersistenceException;
import nl.knaw.huygens.persistence.PersistenceManager;

import org.junit.Before;
import org.junit.Test;

public class PersistenceWrapperTest {

	private PersistenceManager persistenceManager;
	
	@Before
	public void setUp(){
		persistenceManager = mock(PersistenceManager.class);
	}
	
	private PersistenceWrapper createInstance(String url){
		return new PersistenceWrapper(url, persistenceManager);
	}
	
	@Test
	public void testPersistObjectSucces() throws PersistenceException{
		PersistenceWrapper persistenceWrapper = createInstance("http://test.nl");
		persistenceWrapper.persistObject("test", "1234");
		verify(persistenceManager).persistURL("http://test.nl/resources/test/1234");
	}
	
	@Test
	public void testPersistObjectSuccesUrlEndOnSlash() throws PersistenceException{
		PersistenceWrapper persistenceWrapper = createInstance("http://test.nl/");
		persistenceWrapper.persistObject("test", "1234");
		verify(persistenceManager).persistURL("http://test.nl/resources/test/1234");
	}
	
	@SuppressWarnings("unchecked")
	@Test(expected = PersistenceException.class)
	public void testPersistObjectException() throws PersistenceException{
		when(persistenceManager.persistURL(anyString())).thenThrow(PersistenceException.class);
		
		PersistenceWrapper persistenceWrapper = createInstance("http://test.nl/");
		persistenceWrapper.persistObject("test", "1234");
	}
}
