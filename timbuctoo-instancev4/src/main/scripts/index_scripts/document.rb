class Document < Hash

    @@location = ""

    @@wanted_properties = [
	"_id",
	"title",
	"date",
	"documentType",
	"notes",
    ]
    
    @@new_prop_names = [
	"id",
	"displayName_s",
	"date_i",
	"documentType_s",
	"notes_t"
    ]

    @@wanted_relations = [
	"hasPublishLocation",
	"hasWorkLanguage",
	"hasGenre",
	"hasDocumentSource"
    ]

    @@new_rel_names = [
	"publishLocation_ss",
	"language_ss",
	"genre_ss",
	"source_ss"
    ]

    @@wanted_reception_relations = [
	"isBiographyOf",
	"commentsOnPerson",
	"isDedicatedTo",
	"isAwardForPerson",
	"listsPerson",
	"mentionsPerson",
	"isObituaryOf",
	"quotesPerson",
	"referencesPerson"
    ]

    #  eerst e.e.a. van deze relations opslaan;
    #  nadat alle documenten zijn gelezen de relations
    #  verder uitwerken en indexeren
    @@wanted_document_relations = [
	"isEditionOf",
	"isSequelOf",
	"isTranslationOf",
	"isAdaptationOf",
	"isPlagiarismOf",
	"hasAnnotationsOn",
	"isBibliographyOf",
	"isCensoringOf",
	"commentsOnWork",
	"isAnthologyContaining",
	"isCopyOf",
	"isAwardForWork",
	"isPrefaceOf",
	"isIntertextualTo",
	"listsWork",
	"mentionsWork",
	"isParodyOf",
	"quotesWork",
	"referencesWork"
    ]

    attr_reader :id

    def initialize data
	super
	self['type_s'] = "document"
	@@wanted_properties.each_with_index do |property,ind|
	    if !data[property].nil?
		if ind==2
		    self[@@new_prop_names[ind]] = data[property].to_i
		else
		    self[@@new_prop_names[ind]] = data[property]
		end
	    end
	end
	title_t = "#{data['title']} #{data['englishTitle']}".strip
	self['title_t'] = title_t  if !title_t.empty?

	self['modified_l'] = data['^modified']['timeStamp']

	build_relations data

	self['_childDocuments_'] = Array.new
	add_creators data
	add_receptions data
	add_document_receptions data
    end

    def build_relations data
	@@new_rel_names.each_with_index do |rel,ind|
	    if !data['@relations'].nil?
		self[rel] = Array.new
		add_relation data['@relations'],rel,ind
	    end
	end
    end

    def add_relation relations,rel,ind
	if !relations[@@wanted_relations[ind]].nil?
	    relations[@@wanted_relations[ind]].each do |rt|
		self[rel] << rt['displayName']  if rt['accepted']
	    end
	end
    end

    def add_creators data
	if !data['@relations'].nil?
	    is_created_by = data['@relations']['isCreatedBy']
	end
	return if is_created_by.nil?
	is_created_by.each do |creator|
	    person = Persons.find creator['id']
	    if !person.nil?
		# avoid building id's like : "id/id/id/..."
		new_person = person.dup
		new_person['id'] = "#{id}/#{person.id}"
		old_keys = new_person.keys
		old_keys.delete('id')
		old_keys.each do |old_key|
		    new_key = "person_#{old_key}"
		    new_person[new_key] = new_person.delete(old_key)
		end
		self['_childDocuments_'] << new_person
	    end
	end
    end

    def add_receptions data
	doc_id = data["_id"]
	doc_displayName = data["title"]
	reception_relations = Array.new
	if !data['@relations'].nil?
	    @@wanted_reception_relations.each do |rec_rel|
		if !data['@relations'][rec_rel].nil?
		    data['@relations'][rec_rel].each do |rr_data|
			new_rr = Hash.new
			new_rr['id'] = rr_data['relationId']
			new_rr['relationType_s'] = rec_rel
			new_rr['person_id_s'] = rr_data['id']
			new_rr['person_displayName_s'] = rr_data['displayName']
			new_rr['document_id_s'] = doc_id
			new_rr['displayName_s'] = doc_displayName
			reception_relations << new_rr
		    end
		end
	    end
	end


	Documents.person_receptions_concat reception_relations

	return
    end

    def add_document_receptions data
	doc_id = data["_id"]
	doc_displayName = data["title"]
	reception_relations = Array.new
	if !data['@relations'].nil?
	    @@wanted_document_relations.each do |rec_rel|
		if !data['@relations'][rec_rel].nil?
		    data['@relations'][rec_rel].each do |rr_data|
			new_rr = Hash.new
			new_rr['id'] = rr_data['relationId']
			new_rr['reception_id_s'] = doc_id
			new_rr['displayName_s'] = doc_displayName
			new_rr['relationType_s'] = rec_rel
			new_rr['document_id_s'] = rr_data['id']
			new_rr['document_displayName_s'] = rr_data['displayName']
			rel_doc = Documents.find rr_data['id']
			if !rel_doc.nil?
			    # dit komt kennelijk nooit voor?
			    new_rr['_childDocuments_'] = rel_doc
			    # aparte lijst van voltooide receptions
			    # die kan worden gecommit
			    Documents.complete_document_receptions_add new_rr
			else
    			    reception_relations << new_rr
			end
		    end
		end
	    end
	end
	Documents.document_receptions_concat reception_relations
	return
    end

    def id
	self['id']
    end

    def Document.location= location
	@@location = location
    end

end

