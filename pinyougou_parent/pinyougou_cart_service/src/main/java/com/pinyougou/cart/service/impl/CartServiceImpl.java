package com.pinyougou.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.pinyougou.cart.service.CartService;
import com.pinyougou.mapper.TbItemMapper;
import com.pinyougou.pojo.TbItem;
import com.pinyougou.pojo.TbOrderItem;
import com.pinyougou.pojogroup.Cart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 购物车业务逻辑实现
 */
@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private TbItemMapper tbItemMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Override
    public List<Cart> addGoodsToCartList(List<Cart> cartList, Long itemId, Integer num) {
            //1.根据商品SKU ID查询SKU商品信息
        TbItem item = tbItemMapper.selectByPrimaryKey(itemId);
        if (item==null){
               throw  new RuntimeException("商品信息不存在!");
        }
        if (!"1".equals(item.getStatus())){
                throw new RuntimeException("商品状态无效!");
        }
            //2.获取商家ID
        String sellerId = item.getSellerId();
        //3.根据商家ID判断购物车列表中是否存在该商家的购物车
       Cart cart= this.searchCartBySellerId(cartList,sellerId);
            //4.如果购物车列表中不存在该商家的购物车
        if (cart==null){
               //4.1 新建购物车对象
            cart=new Cart();
            cart.setSellerId(sellerId);//商家id
            cart.setSellerName(item.getSeller());//商家名字
            //构建商品列表
            List<TbOrderItem> orderItemList=new ArrayList<>();
            //构建商品信息
            TbOrderItem tbOrderItem=this.createOrderItem(item,num);
            orderItemList.add(tbOrderItem);
            cart.setOrderItemList( orderItemList);
               //4.2 将新建的购物车对象添加到购物车列
               cartList.add(cart);
    }
       else {
           //5.如果购物车列表中存在该商家的购物车
            // 查询购物车明细列表中是否存在该商品
       TbOrderItem orderItem=searchOrderItemByItemId(cart.getOrderItemList(),itemId);
            //5.1. 如果没有，新增购物车明细
            if (orderItem==null){
              //构建新的商品信息
                 orderItem = createOrderItem(item, num);
                 //向商家商品列表中增加一个商品
                cart.getOrderItemList().add(orderItem);
            }else {
                //5.2. 如果有，在原购物车明细上添加数量，更改金额
                orderItem.setNum(orderItem.getNum()+num);
                double totalFree=item.getPrice().doubleValue()*orderItem.getNum();
                orderItem.setTotalFee(new BigDecimal(totalFree));
                //特殊情况
                //修改了商品数量后,如果当前购买的数量不足1
                if (orderItem.getNum()<1){
                  //删除当前商品
                    cart.getOrderItemList().remove(orderItem);
                }
                //如果删除商品后,我们没有要添加的商品了
                if (cart.getOrderItemList().size()<1){
                    //删除整个购物车
                     cartList.remove(cart);
                }
            }
        }
        return cartList;
    }

    /**
     * 从redis中查询购物车
     * @param username
     * @return
     */
    @Override
    public List<Cart> findCartListFromRedis(String username) {
        List<Cart> cartList = (List<Cart>) redisTemplate.boundHashOps("cartList").get(username);
        if (cartList==null){
            cartList=new ArrayList<>();
        }
        return cartList;
    }

    /**
     * 将购物车保存到redis中
     * * @param username
     *
     * @param cartList
     */
    @Override
    public void saveCartListToRedis(String username, List<Cart> cartList) {
   redisTemplate.boundHashOps("cartList").put(username, cartList);
    }
    /**
     * 合并购物车
     * @param cookieList
     * @param redisList
     * @return
     */
    @Override
    public List<Cart> bothCartList(List<Cart> cookieList, List<Cart> redisList) {
        List<Cart> cartList=null;
        for (Cart cart : cookieList) {//遍历cookie中的购物车列表
            for (TbOrderItem orderItem : cart.getOrderItemList()) {//遍历购物车明细
                //合并购物车列表
                cartList = this.addGoodsToCartList(redisList, orderItem.getItemId(), orderItem.getNum());
            }
        }
        return cartList;
    }

    /**
     * 根据商品skuId查询当前商家中有没有相应的商品信息
     * @param orderItemList 当前商家的商品列表
     * @param itemId 要查找的商品Id
     * @return 查找的结果为null,表示找不到
     */
    private TbOrderItem searchOrderItemByItemId(List<TbOrderItem> orderItemList, Long itemId) {
        for (TbOrderItem orderItem : orderItemList) {
            if (orderItem.getItemId().longValue()==itemId.longValue()){
                return orderItem;
            }
        }
        return null;
    }

    /**
     * 构建购物车商品信息
     * @param item 当前要购买的sku信息
     * @param num 购买的数量
     * @return 购物车商品对象
     */
    private TbOrderItem createOrderItem(TbItem item, Integer num) {
        if (num<1){
            throw new RuntimeException("请输入正确的购买数量!");
        }
        TbOrderItem orderItem=new TbOrderItem();
        orderItem.setGoodsId(item.getGoodsId());
        orderItem.setItemId(item.getId());
        orderItem.setNum(num);
        orderItem.setPicPath(item.getImage());
        orderItem.setPrice(item.getPrice());
        orderItem.setSellerId(item.getSellerId());
        orderItem.setTitle(item.getTitle());
        double totalFree=item.getPrice().doubleValue()*num;
        orderItem.setTotalFee(new BigDecimal(totalFree));
        return orderItem;
    }

    /**
     *  根据商家ID查询当前用户的购物车对象信息
     * @param cartList   原来的购物车列表
     * @param sellerId   商家id
     * @return 当前商加购物车对象
     */
    private Cart searchCartBySellerId(List<Cart> cartList, String sellerId) {
        for (Cart cart : cartList) {
            //如果找到该商家的购物车信息
            if (cart.getSellerId().equals(sellerId)){
                //返回购物车对象
                return cart;
            }
        }
        //没有找到返回null
        return null;
    }
}
