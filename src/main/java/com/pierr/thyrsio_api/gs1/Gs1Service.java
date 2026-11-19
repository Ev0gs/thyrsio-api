package com.pierr.thyrsio_api.gs1;

import com.pierr.thyrsio_api.common.BusinessRuleException;
import com.pierr.thyrsio_api.common.ResourceNotFoundException;
import com.pierr.thyrsio_api.gs1.dto.*;
import com.pierr.thyrsio_api.referentiel.ArticleInfo;
import com.pierr.thyrsio_api.referentiel.ArticleLookup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Les gencodes du client : ses préfixes GS1, la génération du gencode suivant, et ce que chacun désigne.
 * Règles : un gencode est unique, sa clé est juste, il ne désigne qu'un produit et un conditionnement,
 * et il n'est jamais redonné (le suivant est toujours après le plus grand déjà pris, retirés compris).
 */
@Service
public class Gs1Service {

    private final Gs1PrefixRepository prefixRepository;
    private final GtinRepository gtinRepository;
    private final ArticleLookup articleLookup;

    public Gs1Service(Gs1PrefixRepository prefixRepository, GtinRepository gtinRepository,
                      ArticleLookup articleLookup) {
        this.prefixRepository = prefixRepository;
        this.gtinRepository = gtinRepository;
        this.articleLookup = articleLookup;
    }

    // ========== Préfixes ==========

    @Transactional(readOnly = true)
    public List<PrefixResponse> listPrefixes() {
        return prefixRepository.findAllByOrderByCreatedAtAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public PrefixResponse createPrefix(PrefixRequest request) {
        String prefix = request.prefix().trim();
        for (Gs1Prefix other : prefixRepository.findAll()) {
            if (other.getPrefix().startsWith(prefix) || prefix.startsWith(other.getPrefix())) {
                throw new BusinessRuleException("Le préfixe " + prefix + " recouvre le préfixe " + other.getPrefix());
            }
        }
        Gs1Prefix created = prefixRepository.save(new Gs1Prefix(prefix, blankToNull(request.label())));
        return toResponse(created);
    }

    @Transactional
    public PrefixResponse updatePrefix(UUID id, PrefixRequest request) {
        Gs1Prefix prefix = prefixRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Préfixe GS1", id));
        if (!prefix.getPrefix().equals(request.prefix().trim())) {
            throw new IllegalArgumentException("Un préfixe ne change pas : ses gencodes en dépendent");
        }
        prefix.update(blankToNull(request.label()), request.active() == null || request.active());
        return toResponse(prefix);
    }

    // ========== Gencodes ==========

    @Transactional(readOnly = true)
    public Page<GtinResponse> list(UUID articleId, GtinPackaging packaging, GtinStatus status, String search,
                                   Pageable pageable) {
        List<Specification<Gtin>> filters = new ArrayList<>();
        if (articleId != null) filters.add((root, query, cb) -> cb.equal(root.get("finishedArticleId"), articleId));
        if (packaging != null) filters.add((root, query, cb) -> cb.equal(root.get("packaging"), packaging));
        if (status != null) filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        if (search != null && !search.isBlank()) {
            String text = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            String digits = Gtins.normalize(search.trim());
            // Le texte cherche aussi dans le produit : on le résout d'abord dans le référentiel
            Set<UUID> articles = articleLookup.findAll(true).stream()
                    .filter(a -> a.finishedGood()
                            && (a.code() + " " + a.label()).toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT)))
                    .map(ArticleInfo::id).collect(Collectors.toSet());
            filters.add((root, query, cb) -> cb.or(
                    cb.like(root.get("gtin"), "%" + digits + "%"),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), text),
                    articles.isEmpty() ? cb.disjunction() : root.get("finishedArticleId").in(articles)));
        }
        Page<Gtin> page = gtinRepository.findAll(Specification.allOf(filters), pageable);
        Map<UUID, ArticleInfo> articles = articleLookup.findAllById(page.stream()
                .map(Gtin::getFinishedArticleId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, String> prefixes = prefixRepository.findAll().stream()
                .collect(Collectors.toMap(Gs1Prefix::getId, Gs1Prefix::getPrefix));
        return page.map(gtin -> toResponse(gtin, articles, prefixes));
    }

    @Transactional(readOnly = true)
    public GtinResponse get(UUID id) {
        return toResponse(find(id));
    }

    /** Le gencode suivant du préfixe : après le plus grand déjà pris, jamais un trou ni un retiré. */
    @Transactional
    public GtinResponse generate(GenerateGtinRequest request, UUID userId) {
        ArticleInfo article = finishedGood(request.finishedArticleId());
        int units = units(request.packaging(), request.unitsPerPack());
        requireFreePackaging(article, request.packaging(), units);

        Gs1Prefix prefix = request.prefixId() != null
                ? prefixRepository.lockById(request.prefixId())
                .orElseThrow(() -> new ResourceNotFoundException("Préfixe GS1", request.prefixId()))
                : firstPrefixWithRoom();
        if (!prefix.isActive()) {
            throw new BusinessRuleException("Le préfixe " + prefix.getPrefix() + " n'est plus actif");
        }
        int referenceDigits = 12 - prefix.getPrefix().length();
        long next = nextReference(prefix);
        if (next >= Gtins.capacity(prefix.getPrefix())) {
            throw new BusinessRuleException("Le préfixe " + prefix.getPrefix() + " est plein ("
                    + Gtins.capacity(prefix.getPrefix()) + " gencodes) : ajoutez un nouveau préfixe GS1");
        }
        String gtin = Gtins.withCheckDigit(prefix.getPrefix() + ("%0" + referenceDigits + "d").formatted(next));

        Gtin created = new Gtin(gtin, prefix.getId(), request.packaging(), units,
                blankToNull(request.description()), blankToNull(request.notes()), userId);
        created.assign(article.id(), request.packaging(), units);
        return toResponse(gtinRepository.saveAndFlush(created));
    }

    /** Un gencode créé avant Thyrsio : sa clé est vérifiée, il ne doit pas déjà exister. */
    @Transactional
    public GtinResponse record(RecordGtinRequest request, UUID userId) {
        String gtin = Gtins.normalize(request.gtin());
        if (!Gtins.isValid(gtin)) {
            throw new IllegalArgumentException("« " + request.gtin() + " » n'est pas un EAN-13 valide "
                    + "(13 chiffres, dont la clé de contrôle)");
        }
        if (gtinRepository.existsByGtin(gtin)) {
            throw new BusinessRuleException("Le gencode " + gtin + " est déjà enregistré");
        }
        int units = units(request.packaging(), request.unitsPerPack());
        UUID prefixId = prefixRepository.findAll().stream()
                .filter(p -> gtin.startsWith(p.getPrefix())).map(Gs1Prefix::getId).findFirst().orElse(null);
        Gtin created = new Gtin(gtin, prefixId, request.packaging(), units,
                blankToNull(request.description()), blankToNull(request.notes()), userId);
        if (request.finishedArticleId() != null) {
            ArticleInfo article = finishedGood(request.finishedArticleId());
            requireFreePackaging(article, request.packaging(), units);
            created.assign(article.id(), request.packaging(), units);
        }
        return toResponse(gtinRepository.saveAndFlush(created));
    }

    @Transactional
    public GtinResponse assign(UUID id, AssignGtinRequest request) {
        Gtin gtin = find(id);
        ArticleInfo article = finishedGood(request.finishedArticleId());
        int units = units(request.packaging(), request.unitsPerPack());
        requireFreePackaging(article, request.packaging(), units);
        gtin.assign(article.id(), request.packaging(), units);
        return toResponse(gtinRepository.saveAndFlush(gtin));
    }

    @Transactional
    public GtinResponse describe(UUID id, DescribeGtinRequest request) {
        Gtin gtin = find(id);
        gtin.describe(blankToNull(request.description()), blankToNull(request.notes()));
        return toResponse(gtin);
    }

    @Transactional
    public GtinResponse retire(UUID id) {
        Gtin gtin = find(id);
        gtin.retire();
        return toResponse(gtin);
    }

    // ========== Méthodes utilitaires ==========

    private Gs1Prefix firstPrefixWithRoom() {
        List<Gs1Prefix> active = prefixRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(Gs1Prefix::isActive).toList();
        if (active.isEmpty()) {
            throw new BusinessRuleException("Aucun préfixe GS1 actif : ajoutez celui de l'entreprise");
        }
        for (Gs1Prefix prefix : active) {
            if (nextReference(prefix) < Gtins.capacity(prefix.getPrefix())) {
                return prefixRepository.lockById(prefix.getId()).orElseThrow();
            }
        }
        throw new BusinessRuleException("Tous les préfixes GS1 sont pleins : ajoutez un nouveau préfixe");
    }

    /** La référence suivante du préfixe : après la plus grande déjà prise (la référence 0 n'est pas utilisée). */
    private long nextReference(Gs1Prefix prefix) {
        int start = prefix.getPrefix().length();
        return gtinRepository.findGtinsLike(prefix.getPrefix() + "%").stream()
                .mapToLong(gtin -> Long.parseLong(gtin.substring(start, 12)))
                .max().orElse(0) + 1;
    }

    private ArticleInfo finishedGood(UUID articleId) {
        ArticleInfo article = articleLookup.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article inconnu : " + articleId));
        if (!article.finishedGood()) {
            throw new IllegalArgumentException(article.code() + " n'est pas un produit fini : "
                    + "seuls les produits finis reçoivent un gencode");
        }
        if (!article.active()) {
            throw new BusinessRuleException("Le produit " + article.code() + " est archivé");
        }
        return article;
    }

    /** Une bouteille, c'est 1 ; un groupé, au moins 2. */
    private static int units(GtinPackaging packaging, Integer unitsPerPack) {
        if (packaging == GtinPackaging.BOTTLE) {
            return 1;
        }
        if (unitsPerPack == null || unitsPerPack < 2) {
            throw new IllegalArgumentException("Indiquez le nombre de bouteilles du groupé (au moins 2)");
        }
        return unitsPerPack;
    }

    private void requireFreePackaging(ArticleInfo article, GtinPackaging packaging, int units) {
        if (packaging != GtinPackaging.OTHER && gtinRepository.existsByFinishedArticleIdAndPackagingAndUnitsPerPackAndStatus(
                article.id(), packaging, units, GtinStatus.ACTIVE)) {
            throw new BusinessRuleException(article.code() + " a déjà un gencode actif pour "
                    + describePackaging(packaging, units) + " : retirez-le d'abord pour en créer un autre");
        }
    }

    static String describePackaging(GtinPackaging packaging, int units) {
        return packaging == GtinPackaging.BOTTLE ? "la bouteille"
                : packaging.label().toLowerCase(Locale.ROOT) + " de " + units;
    }

    private Gtin find(UUID id) {
        return gtinRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Gencode", id));
    }

    private PrefixResponse toResponse(Gs1Prefix prefix) {
        long capacity = Gtins.capacity(prefix.getPrefix());
        long used = gtinRepository.countLike(prefix.getPrefix() + "%");
        return new PrefixResponse(prefix.getId(), prefix.getPrefix(), prefix.getLabel(), prefix.isActive(),
                capacity, used, Math.max(0, capacity - used));
    }

    private GtinResponse toResponse(Gtin gtin) {
        Map<UUID, ArticleInfo> articles = gtin.getFinishedArticleId() == null ? Map.of()
                : articleLookup.findAllById(List.of(gtin.getFinishedArticleId()));
        Map<UUID, String> prefixes = prefixRepository.findAll().stream()
                .collect(Collectors.toMap(Gs1Prefix::getId, Gs1Prefix::getPrefix));
        return toResponse(gtin, articles, prefixes);
    }

    private static GtinResponse toResponse(Gtin gtin, Map<UUID, ArticleInfo> articles, Map<UUID, String> prefixes) {
        ArticleInfo article = gtin.getFinishedArticleId() != null ? articles.get(gtin.getFinishedArticleId()) : null;
        return new GtinResponse(gtin.getId(), gtin.getGtin(), gtin.getPrefixId(),
                gtin.getPrefixId() != null ? prefixes.get(gtin.getPrefixId()) : null,
                gtin.getFinishedArticleId(), article != null ? article.code() : null,
                article != null ? article.label() : null, gtin.getPackaging(), gtin.getPackaging().label(),
                gtin.getUnitsPerPack(), gtin.getDescription(), gtin.getStatus(), gtin.getNotes(),
                gtin.getCreatedAt(), gtin.getAssignedAt(), gtin.getRetiredAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
