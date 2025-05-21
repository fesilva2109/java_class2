package br.com.fiap.pedidoservice.service;

import br.com.fiap.pedidoservice.dto.ProdutoDTO;
import br.com.fiap.pedidoservice.model.ItemPedido;
import br.com.fiap.pedidoservice.model.Pedido;
import br.com.fiap.pedidoservice.repository.PedidoRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class PedidoService {
    private static final Logger logger = LoggerFactory.getLogger(PedidoService.class);
    private final PedidoRepository pedidoRepository;
    private final RestTemplate restTemplate;

    @Value("${catalogo.service.url}")
    private String catalogoServiceUrl;

    @Autowired
    public PedidoService(PedidoRepository pedidoRepository, RestTemplate restTemplate) {
        this.pedidoRepository = pedidoRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public Pedido criarPedido(Pedido pedido) {
        logger.info("Criando pedido para o cliente: {}", pedido.getIdCliente());
        double valorTotalCalculado = 0;

        for (ItemPedido item : pedido.getItens()) {
            // idProduto e qtd
            String urlProduto = catalogoServiceUrl + "/produtos/" + item.getIdProduto();
            logger.info("Consultando produto no catálogo service: {}", urlProduto);
            try {
                ProdutoDTO produto = restTemplate.getForObject(urlProduto, ProdutoDTO.class);
                if (produto != null) {
                    logger.info("Produto {} encontrado: {}. Preço: {}", produto.id(), produto.nome(), produto.preco());
                    item.setPrecoUnitario(produto.preco());
                    valorTotalCalculado += produto.preco() * item.getQuantidade();
                } else {
                    logger.warn("Produto com id {} não encontrado no catálogo.", item.getIdProduto());
                    throw new RuntimeException("Produto não encontrado no catálogo. ID: " + item.getIdProduto());
                }
            } catch (HttpClientErrorException.NotFound e) {
                logger.warn("Produto com id {} não encontrado no catálogo (404).", item.getIdProduto());
                throw new RuntimeException("Produto não encontrado no catálogo. ID: " + item.getIdProduto())
            } catch (Exception e) {
                logger.error("Erro ao consultar produto no catálogo: {}", e.getMessage());
                throw new RuntimeException("Erro ao consultar produto." + e.getMessage());
            }
        }
        pedido.setValorTotal(valorTotalCalculado);
        pedido.setStatus("PENDENTE");
        Pedido pedidoSalvo = pedidoRepository.save(pedido);
        logger.info("Pedido id {} criado com sucesso. Valor total: {}", pedidoSalvo.getId(), pedidoSalvo.getValorTotal());
        return pedidoSalvo;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }
}
